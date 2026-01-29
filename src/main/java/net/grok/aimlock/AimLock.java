package net.grok.aimlock;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class AimLock implements ClientModInitializer {
    public static KeyBinding toggleKey;
    public static boolean enabled = false;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimlock.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "key.categories.aimlock"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Toggle logic
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
            }
            if (!enabled || client.player == null || client.world == null) {
                return;
            }

            // Get exact crosshair target (vanilla raycast)
            double reach = client.interactionManager.getReachDistance();
            float tickDelta = client.getTickDelta();
            HitResult hitResult = client.player.raycast(reach, tickDelta, false);
            if (hitResult.getType() != HitResult.Type.ENTITY) {
                return;
            }

            EntityHitResult entityHit = (EntityHitResult) hitResult;
            Entity target = entityHit.getEntity();
            if (!(target instanceof PlayerEntity player) ||
                player == client.player ||
                !player.isAlive() ||
                player.isSpectator()) {
                return;
            }

            // Calculate target eye position
            Vec3d eyePos = client.player.getEyePos();
            Vec3d targetEyePos = ((LivingEntity) target).getEyePos(tickDelta);

            // Compute target yaw/pitch
            double dx = targetEyePos.x - eyePos.x;
            double dy = targetEyePos.y - eyePos.y;
            double dz = targetEyePos.z - eyePos.z;
            double horizDist = Math.sqrt(dx * dx + dz * dz);

            float targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
            float targetPitch = (float) -(Math.atan2(dy, horizDist) * 180.0 / Math.PI);

            // Smooth deltas (adjust speeds below for snappier/smoother)
            float yawDiff = MathHelper.wrapDegrees(targetYaw - client.player.getYaw());
            float pitchDiff = targetPitch - client.player.getPitch();  // No wrap for pitch

            float maxYawSpeed = 12.0f;  // Higher = snappier (try 8-15)
            float maxPitchSpeed = 12.0f;

            // Apply smooth lock
            client.player.setYaw(client.player.getYaw() + MathHelper.clamp(yawDiff, -maxYawSpeed, maxYawSpeed));
            client.player.setPitch(client.player.getPitch() + MathHelper.clamp(pitchDiff, -maxPitchSpeed, maxPitchSpeed));
        });
