// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.showcase.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val DANCE_DURATIONS = floatArrayOf(0.3f, 0.2f, 0.3f, 0.3f, 0.2f, 0.3f)
private val SIT_DURATIONS = floatArrayOf(0.6f, 0.5f, 0.6f, 0.4f)
private val NOTICE_DURATIONS = floatArrayOf(0.35f, 1.35f, 0.35f, 0.45f)
private val ANGRY_DURATIONS = floatArrayOf(0.45f, 0.9f, 0.55f, 0.45f)

private fun easeInOut(t: Float) = t * t * (3f - 2f * t)
private fun Offset.lerpTo(other: Offset, t: Float) = Offset(lerp(x, other.x, t), lerp(y, other.y, t))

private enum class DancePhase {
  MOVE_TO_LEFT, HOLD_LEFT, RETURN_FROM_LEFT,
  MOVE_TO_RIGHT, HOLD_RIGHT, RETURN_FROM_RIGHT;
}

private enum class NoticePhase {
  OPEN_MOUTH, TALK, CLOSE_MOUTH, HOLD_SMILE;

  companion object {
    val ORDER = entries
  }
}

private enum class AngryPhase {
  WIND_UP, SHAKE, RETURN, RECOVER;

  companion object {
    val ORDER = entries
  }
}

private data class AngryPose(
  val leftPalmWorldPos: Offset,
  val rightPalmWorldPos: Offset,
  val leftFootWorldPos: Offset,
  val rightFootWorldPos: Offset,
  val leftArmArcDir: Float,
  val rightArmArcDir: Float,
  val leftLegArcDir: Float,
  val rightLegArcDir: Float,
)

private data class ArmTargets(
  val leftPalmWorldPos: Offset,
  val rightPalmWorldPos: Offset,
)

private fun loopAnimation(
  phaseOrdinal: Int,
  phaseTime: Float,
  durations: FloatArray,
  phaseCount: Int,
): Pair<Int, Float> {
  val duration = durations[phaseOrdinal]
  val next = phaseTime.coerceAtLeast(0f)
  return if (next >= duration) {
    Pair((phaseOrdinal + 1) % phaseCount, next - duration)
  }
  else {
    Pair(phaseOrdinal, next)
  }
}

/**
 * Builds a symmetric raised-arm pose with palms pushed outward from the torso.
 *
 * Keeping the palms far from the center helps because Kodee's arms render behind the body fill,
 * so targets that drift too close to `x = 0` are easily occluded by the torso.
 */
private fun raisedArmsOutwardPose(
  palmY: Float,
  @Suppress("SameParameterValue") palmSpreadX: Float = 0.8f,
  leftWaveOffsetX: Float = 0f,
  rightWaveOffsetX: Float = 0f,
): ArmTargets = ArmTargets(
  leftPalmWorldPos = Offset(-palmSpreadX + leftWaveOffsetX, palmY),
  rightPalmWorldPos = Offset(palmSpreadX + rightWaveOffsetX, palmY),
)

private enum class SitPhase {
  SIT_DOWN, HOLD_SIT, STAND_UP, HOLD_STAND;

  companion object {
    val ORDER = entries
  }
}

@Composable
fun KodeeStanding2D(modifier: Modifier = Modifier.fillMaxSize()) {
  val (x1, y1) = 0.7f to -0.3f
  val (x2, y2) = 0.9f to -0.2f
  val x = remember { Animatable(x1) }
  val y = remember { Animatable(y1) }
  val anim = tween<Float>(500, easing = LinearEasing)
  LaunchedEffect(Unit) {
    launch { x.animateTo(x2, infiniteRepeatable(anim, RepeatMode.Reverse)) }
    launch { y.animateTo(y2, infiniteRepeatable(anim, RepeatMode.Reverse)) }
  }

  Kodee2D(
    modifier = modifier,
    leftPalmWorldPos = Offset(-0.7f, 0.6f),
    rightPalmWorldPos = Offset(x.value, y.value),
    rightArmArcDir = 1f,
    rightLegArcDir = 0f,
    leftLegArcDir = 0f,
    bodyRotation = -0f,
    leftEyeWinkProgress = 1f,
    rightEyeWinkProgress = 1f,
    eyeWinkDir = 1f,
  )
}

@Composable
fun KodeeDance2D(modifier: Modifier = Modifier.fillMaxSize()) {
  var phase by remember { mutableStateOf(DancePhase.MOVE_TO_LEFT) }
  var phaseTime by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(Unit) {
    var lastNanos = withFrameNanos { it }
    while (true) {
      withFrameNanos { nanos ->
        val delta = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastNanos = nanos
        val (nextPhaseOrdinal, nextPhaseTime) = loopAnimation(
          phaseOrdinal = phase.ordinal,
          phaseTime = phaseTime + delta,
          durations = DANCE_DURATIONS,
          phaseCount = DancePhase.entries.size,
        )
        phase = DancePhase.entries[nextPhaseOrdinal]
        phaseTime = nextPhaseTime
      }
    }
  }

  val easedT = easeInOut((phaseTime / DANCE_DURATIONS[phase.ordinal]).coerceIn(0f, 1f))
  val bodyOffset = when (phase) {
    DancePhase.MOVE_TO_LEFT -> Offset(-0.08f * easedT, 0.12f - 0.06f * easedT)
    DancePhase.HOLD_LEFT -> Offset(-0.08f, 0.06f)
    DancePhase.RETURN_FROM_LEFT -> Offset(-0.08f * (1f - easedT), 0.12f - 0.06f * (1f - easedT))
    DancePhase.MOVE_TO_RIGHT -> Offset(0.08f * easedT, 0.12f - 0.06f * easedT)
    DancePhase.HOLD_RIGHT -> Offset(0.08f, 0.06f)
    DancePhase.RETURN_FROM_RIGHT -> Offset(0.08f * (1f - easedT), 0.12f - 0.06f * (1f - easedT))
  }
  val bodyRotation = when (phase) {
    DancePhase.MOVE_TO_LEFT -> -30f * easedT
    DancePhase.HOLD_LEFT -> -30f
    DancePhase.RETURN_FROM_LEFT -> -30f * (1f - easedT)
    DancePhase.MOVE_TO_RIGHT -> 30f * easedT
    DancePhase.HOLD_RIGHT -> 30f
    DancePhase.RETURN_FROM_RIGHT -> 30f * (1f - easedT)
  }

  Kodee2D(
    modifier = modifier,
    bodyOffset = bodyOffset,
    leftPalmWorldPos = Offset(bodyOffset.x - 0.8f, bodyOffset.y + 0.6f),
    rightPalmWorldPos = Offset(bodyOffset.x + 0.8f, bodyOffset.y + 0.6f),
    leftFootWorldPos = Offset(bodyOffset.x - 0.26f, bodyOffset.y + 1.1f),
    rightFootWorldPos = Offset(bodyOffset.x + 0.26f, bodyOffset.y + 1.1f),
    leftArmArcDir = 1f,
    rightArmArcDir = -1f,
    leftLegArcDir = 0f,
    rightLegArcDir = 0f,
    bodyRotation = bodyRotation,
    leftEyeWinkProgress = 0f,
    rightEyeWinkProgress = 0f,
    eyeWinkDir = 0f,
    mouthCurveDir = -1.7f,
  )
}

@Composable
fun KodeeSitDown2D(modifier: Modifier = Modifier.fillMaxSize()) {
  var phase by remember { mutableStateOf(SitPhase.SIT_DOWN) }
  var phaseTime by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(Unit) {
    var lastNanos = withFrameNanos { it }
    while (true) {
      withFrameNanos { nanos ->
        val delta = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastNanos = nanos
        val (nextPhaseOrdinal, nextPhaseTime) = loopAnimation(
          phaseOrdinal = phase.ordinal,
          phaseTime = phaseTime + delta,
          durations = SIT_DURATIONS,
          phaseCount = SitPhase.ORDER.size,
        )
        phase = SitPhase.ORDER[nextPhaseOrdinal]
        phaseTime = nextPhaseTime
      }
    }
  }

  val easedT = easeInOut((phaseTime / SIT_DURATIONS[phase.ordinal]).coerceIn(0f, 1f))
  val sitT = when (phase) {
    SitPhase.SIT_DOWN -> easedT
    SitPhase.HOLD_SIT -> 1f
    SitPhase.STAND_UP -> 1f - easedT
    SitPhase.HOLD_STAND -> 0f
  }

  Kodee2D(
    modifier = modifier,
    bodyOffset = Offset(0f, 0.4f * sitT),
    leftPalmWorldPos = Offset(-1f, 0.1f),
    rightPalmWorldPos = Offset(1f, 0.1f),
    leftFootWorldPos = Offset(-0.3f, 1f),
    rightFootWorldPos = Offset(0.3f, 1f),
    leftArmArcDir = 1f,
    rightArmArcDir = -1f,
    leftLegArcDir = 1f,
    rightLegArcDir = -1f,
    leftEyeWinkProgress = sitT,
    rightEyeWinkProgress = sitT,
    eyeWinkDir = 0f,
  )
}

@Composable
fun KodeeNoticeMe2D(modifier: Modifier = Modifier.fillMaxSize()) {
  var phase by remember { mutableStateOf(NoticePhase.OPEN_MOUTH) }
  var phaseTime by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(Unit) {
    var lastNanos = withFrameNanos { it }
    while (true) {
      withFrameNanos { nanos ->
        val delta = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastNanos = nanos
        val (nextPhaseOrdinal, nextPhaseTime) = loopAnimation(
          phaseOrdinal = phase.ordinal,
          phaseTime = phaseTime + delta,
          durations = NOTICE_DURATIONS,
          phaseCount = NoticePhase.ORDER.size,
        )
        phase = NoticePhase.ORDER[nextPhaseOrdinal]
        phaseTime = nextPhaseTime
      }
    }
  }

  val phaseProgress = (phaseTime / NOTICE_DURATIONS[phase.ordinal]).coerceIn(0f, 1f)
  val easedT = easeInOut(phaseProgress)
  val talkWave = sin(phaseProgress * 6f * PI.toFloat()).let { (it + 1f) * 0.5f }
  val wave = sin(phaseProgress * 2f * PI.toFloat())
  val armBaseLift = 0.1f * sin(phaseProgress * PI.toFloat()).coerceAtLeast(0f)

  val mouthCurveProgress = when (phase) {
    NoticePhase.OPEN_MOUTH -> 1f - easedT
    NoticePhase.TALK -> 0.18f + 0.47f * talkWave
    NoticePhase.CLOSE_MOUTH -> easedT
    NoticePhase.HOLD_SMILE -> 1f
  }
  val armTargets = raisedArmsOutwardPose(
    palmY = -0.2f - armBaseLift,
    palmSpreadX = 0.8f,
    leftWaveOffsetX = 0.2f * wave,
    rightWaveOffsetX = -0.1f * wave,
  )

  val bodyOffset = when (phase) {
    NoticePhase.OPEN_MOUTH -> Offset(0f, -0.01f * easedT)
    NoticePhase.TALK -> Offset(0f, -0.02f - 0.015f * sin(phaseProgress * 2f * PI.toFloat()))
    NoticePhase.CLOSE_MOUTH -> Offset(0f, -0.01f * (1f - easedT))
    NoticePhase.HOLD_SMILE -> Offset.Zero
  }

  Kodee2D(
    modifier = modifier,
    bodyOffset = bodyOffset,
    leftPalmWorldPos = armTargets.leftPalmWorldPos,
    rightPalmWorldPos = armTargets.rightPalmWorldPos,
    leftFootWorldPos = Offset(-0.25f, 1.14f),
    rightFootWorldPos = Offset(0.25f, 1.14f),
    leftArmArcDir = -1f,
    rightArmArcDir = 1f,
    leftLegArcDir = 0f,
    rightLegArcDir = 0f,
    bodyRotation = 6f * wave,
    headRotation = 4f * wave,
    leftEyeWinkProgress = 0f,
    rightEyeWinkProgress = 0f,
    eyeWinkDir = 0f,
    mouthCurveDir = -1f,
    mouthCurveProgress = mouthCurveProgress,
    mouthSizeMultiplier = 1.05f,
  )
}

@Composable
fun KodeeAngry2D(modifier: Modifier = Modifier.fillMaxSize()) {
  var phase by remember { mutableStateOf(AngryPhase.WIND_UP) }
  var phaseTime by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(Unit) {
    var lastNanos = withFrameNanos { it }
    while (true) {
      withFrameNanos { nanos ->
        val delta = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastNanos = nanos
        val (nextPhaseOrdinal, nextPhaseTime) = loopAnimation(
          phaseOrdinal = phase.ordinal,
          phaseTime = phaseTime + delta,
          durations = ANGRY_DURATIONS,
          phaseCount = AngryPhase.ORDER.size,
        )
        phase = AngryPhase.ORDER[nextPhaseOrdinal]
        phaseTime = nextPhaseTime
      }
    }
  }

  val startPose = AngryPose(
    leftPalmWorldPos = Offset(-0.88f, 0.24f),
    rightPalmWorldPos = Offset(0.88f, 0.24f),
    leftFootWorldPos = Offset(-0.23f, 1.12f),
    rightFootWorldPos = Offset(0.23f, 1.12f),
    leftArmArcDir = 0.2f,
    rightArmArcDir = -0.2f,
    leftLegArcDir = 0.15f,
    rightLegArcDir = -0.15f,
  )
  val poseA = AngryPose(
    leftPalmWorldPos = Offset(-0.74f, -0.14f),
    rightPalmWorldPos = Offset(0.7f, -0.24f),
    leftFootWorldPos = Offset(-0.12f, 1.02f),
    rightFootWorldPos = Offset(0.12f, 1.03f),
    leftArmArcDir = -1f,
    rightArmArcDir = 1f,
    leftLegArcDir = -0.8f,
    rightLegArcDir = 0.8f,
  )
  val poseB = AngryPose(
    leftPalmWorldPos = Offset(-0.62f, -0.34f),
    rightPalmWorldPos = Offset(0.82f, -0.08f),
    leftFootWorldPos = Offset(-0.1f, 1.05f),
    rightFootWorldPos = Offset(0.08f, 0.99f),
    leftArmArcDir = 1f,
    rightArmArcDir = -1f,
    leftLegArcDir = 0.9f,
    rightLegArcDir = -0.9f,
  )
  val poseC = AngryPose(
    leftPalmWorldPos = Offset(-0.56f, -0.28f),
    rightPalmWorldPos = Offset(0.56f, -0.3f),
    leftFootWorldPos = Offset(-0.06f, 1.0f),
    rightFootWorldPos = Offset(0.06f, 1.0f),
    leftArmArcDir = -0.9f,
    rightArmArcDir = 0.9f,
    leftLegArcDir = -0.7f,
    rightLegArcDir = 0.7f,
  )

  fun interpolatePose(from: AngryPose, to: AngryPose, t: Float): AngryPose = AngryPose(
    leftPalmWorldPos = from.leftPalmWorldPos.lerpTo(to.leftPalmWorldPos, t),
    rightPalmWorldPos = from.rightPalmWorldPos.lerpTo(to.rightPalmWorldPos, t),
    leftFootWorldPos = from.leftFootWorldPos.lerpTo(to.leftFootWorldPos, t),
    rightFootWorldPos = from.rightFootWorldPos.lerpTo(to.rightFootWorldPos, t),
    leftArmArcDir = lerp(from.leftArmArcDir, to.leftArmArcDir, t),
    rightArmArcDir = lerp(from.rightArmArcDir, to.rightArmArcDir, t),
    leftLegArcDir = lerp(from.leftLegArcDir, to.leftLegArcDir, t),
    rightLegArcDir = lerp(from.rightLegArcDir, to.rightLegArcDir, t),
  )

  val phaseProgress = (phaseTime / ANGRY_DURATIONS[phase.ordinal]).coerceIn(0f, 1f)
  val easedT = easeInOut(phaseProgress)
  val shakeWave = sin(phaseProgress * 8f * PI.toFloat())
  val pose = when (phase) {
    AngryPhase.WIND_UP -> interpolatePose(startPose, poseA, easedT)
    AngryPhase.SHAKE -> {
      val segment = phaseProgress * 3f
      when {
        segment < 1f -> interpolatePose(poseA, poseB, easeInOut(segment))
        segment < 2f -> interpolatePose(poseB, poseC, easeInOut(segment - 1f))
        else -> interpolatePose(poseC, poseA, easeInOut(segment - 2f))
      }
    }
    AngryPhase.RETURN -> interpolatePose(poseA, startPose, easedT)
    AngryPhase.RECOVER -> startPose
  }

  val eyeWinkProgress = when (phase) {
    AngryPhase.WIND_UP -> 0.22f + 0.08f * sin(phaseProgress * 2f * PI.toFloat())
    AngryPhase.SHAKE -> 0.12f + 0.2f * ((shakeWave + 1f) * 0.5f)
    AngryPhase.RETURN -> 0.1f * (1f - easedT)
    AngryPhase.RECOVER -> 0f
  }
  val mouthCurveProgress = when (phase) {
    AngryPhase.WIND_UP -> 1f - 0.9f * easedT
    AngryPhase.SHAKE -> 0.02f
    AngryPhase.RETURN -> 0.1f + 0.9f * easedT
    AngryPhase.RECOVER -> 1f
  }
  val mouthSizeMultiplier = when (phase) {
    AngryPhase.WIND_UP -> lerp(1f, 1.6f, easedT)
    AngryPhase.SHAKE -> 1.3f + 0.55f * ((sin(phaseProgress * 10f * PI.toFloat()) + 1f) * 0.5f)
    AngryPhase.RETURN -> lerp(1.4f, 1f, easedT)
    AngryPhase.RECOVER -> 1f
  }
  val bodyOffset = when (phase) {
    AngryPhase.WIND_UP -> Offset(0.02f * shakeWave, 0.02f * easedT)
    AngryPhase.SHAKE -> Offset(0.05f * shakeWave, -0.015f * cos(phaseProgress * 10f * PI.toFloat()))
    AngryPhase.RETURN -> Offset(0.03f * shakeWave * (1f - easedT), 0.01f * (1f - easedT))
    AngryPhase.RECOVER -> Offset.Zero
  }
  val bodyRotation = when (phase) {
    AngryPhase.WIND_UP -> lerp(0f, -10f, easedT)
    AngryPhase.SHAKE -> 12f * shakeWave
    AngryPhase.RETURN -> lerp(-6f, 0f, easedT)
    AngryPhase.RECOVER -> 0f
  }
  val headRotation = when (phase) {
    AngryPhase.WIND_UP -> lerp(0f, -6f, easedT)
    AngryPhase.SHAKE -> 9f * -shakeWave
    AngryPhase.RETURN -> lerp(-4f, 0f, easedT)
    AngryPhase.RECOVER -> 0f
  }

  Kodee2D(
    modifier = modifier,
    bodyOffset = bodyOffset,
    leftPalmWorldPos = pose.leftPalmWorldPos,
    rightPalmWorldPos = pose.rightPalmWorldPos,
    leftFootWorldPos = pose.leftFootWorldPos,
    rightFootWorldPos = pose.rightFootWorldPos,
    leftArmArcDir = pose.leftArmArcDir,
    rightArmArcDir = pose.rightArmArcDir,
    leftLegArcDir = pose.leftLegArcDir,
    rightLegArcDir = pose.rightLegArcDir,
    bodyRotation = bodyRotation,
    headRotation = headRotation,
    leftEyeWinkProgress = eyeWinkProgress,
    rightEyeWinkProgress = eyeWinkProgress,
    eyeWinkDir = 1f,
    mouthCurveDir = 1f,
    mouthCurveProgress = mouthCurveProgress,
    mouthSizeMultiplier = mouthSizeMultiplier,
  )
}

@Composable
fun Kodee3DSpinning(modifier: Modifier = Modifier.fillMaxSize()) {
  val hold1 = 0
  val spin1 = 1
  val hold2 = 2
  val spin2 = 3
  val hold3 = 4
  val spin3 = 5
  val holdDuration = 1.4f
  val spinDuration = 1.8f
  val spin1Speed = 7f
  val spin2Speed = 14f
  val spin3Speed = 28f
  val basePositionY = 0f
  val basePositionZ = 0f
  val spinBounceAmplitude = 0.16f
  val fullTurn = (Math.PI * 2).toFloat()

  fun normalizeRotation(angle: Float): Float {
    var normalized = angle % fullTurn
    if (normalized < 0f) normalized += fullTurn
    return normalized
  }

  var rotationY by remember { mutableFloatStateOf(0f) }
  var positionY by remember { mutableFloatStateOf(basePositionY) }
  var positionZ by remember { mutableFloatStateOf(basePositionZ) }
  var phase by remember { mutableStateOf(hold1) }
  var phaseTime by remember { mutableFloatStateOf(0f) }

  fun advancePhase() {
    phaseTime = 0f
    phase = when (phase) {
      hold1 -> spin1
      spin1 -> hold2
      hold2 -> spin2
      spin2 -> hold3
      hold3 -> spin3
      else -> hold1
    }
    when (phase) {
      hold1, hold2, hold3 -> {
        rotationY = 0f
        positionY = basePositionY
        positionZ = basePositionZ
      }

      else -> positionZ = 1f
    }
  }

  LaunchedEffect(Unit) {
    var lastNanos = withFrameNanos { it }
    while (true) {
      withFrameNanos { nanos ->
        var dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastNanos = nanos
        while (dt > 0f) {
          val duration = when (phase) {
            hold1, hold2, hold3 -> holdDuration
            else -> spinDuration
          }
          val remainingInPhase = (duration - phaseTime).coerceAtLeast(0f)
          if (remainingInPhase <= 0f) {
            advancePhase()
            continue
          }

          val step = minOf(dt, remainingInPhase)
          when (phase) {
            hold1, hold2, hold3 -> {
              phaseTime += step
              rotationY = 0f
              positionY = basePositionY
            }

            spin1 -> {
              phaseTime += step
              rotationY = normalizeRotation(rotationY + spin1Speed * step)
              positionY = basePositionY + sin(phaseTime * spin1Speed * 0.7f) * spinBounceAmplitude
            }

            spin2 -> {
              phaseTime += step
              rotationY = normalizeRotation(rotationY + spin2Speed * step)
              positionY = basePositionY + sin(phaseTime * spin2Speed * 0.7f) * spinBounceAmplitude
            }

            spin3 -> {
              phaseTime += step
              rotationY = normalizeRotation(rotationY + spin3Speed * step)
              positionY = basePositionY + sin(phaseTime * spin3Speed * 0.7f) * spinBounceAmplitude
            }
          }

          dt -= step
          if (phaseTime >= duration) {
            advancePhase()
          }
        }
      }
    }
  }

  Kodee3D(
    modifier = modifier,
    rotationY = rotationY,
    rotationX = 0.1f,
    rotationZ = 0f,
    positionX = 0f,
    positionY = positionY,
    positionZ = positionZ,
  )
}

@Preview
@Composable
private fun KodeePreview() {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    if (maxWidth >= maxHeight) {
      Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
          KodeeStanding2D(Modifier.fillMaxWidth().weight(1f))
          KodeeNoticeMe2D(Modifier.fillMaxWidth().weight(1f))
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
          KodeeDance2D(Modifier.fillMaxWidth().weight(1f))
          KodeeAngry2D(Modifier.fillMaxWidth().weight(1f))
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
          KodeeSitDown2D(Modifier.fillMaxWidth().weight(1f))
          Kodee3DSpinning(Modifier.fillMaxWidth().weight(1f))
        }
      }
    }
    else {
      Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
          KodeeStanding2D(Modifier.weight(1f).fillMaxHeight())
          KodeeDance2D(Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
          KodeeSitDown2D(Modifier.weight(1f).fillMaxHeight())
          KodeeNoticeMe2D(Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
          KodeeAngry2D(Modifier.weight(1f).fillMaxHeight())
          Kodee3DSpinning(Modifier.weight(1f).fillMaxHeight())
        }
      }
    }
  }
}
