// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.showcase.util

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val KODEE_AMBIENT = 0.28f
private const val KODEE_DIFFUSE_STRENGTH = 0.72f
private const val KODEE_FOV = 5f
private const val KODEE_SCALE_FACTOR = 0.6f
private const val KODEE_LIGHT_Y = 0.5f
private const val KODEE_LIGHT_Z = -0.86f

private val KodeePurple = Color(0xFF7B5CF0)
private val KodeeFace = Color(0xFF0D0D1A)
private val KodeePurpleDark = Color(0xFF3D2B8A)
private val KodeePurpleLight = Color(0xFFA78BFA)

private data class Vec3(val x: Float, val y: Float, val z: Float)
private data class KodeeFace3D(
  val verts: List<Vec3>,
  val baseColor: Color,
  val lit: Boolean = true,
  val layer: Int = 0,
  val backFaceCull: Boolean = false,
  val visibleNormalZThreshold: Float = 0f,
)

private class RenderedFace(val screenPts: FloatArray, val vertCount: Int) {
  var avgZ: Float = 0f
  var color: Color = Color.Unspecified
  var layer: Int = 0
}

private val KODEE_MESH: List<KodeeFace3D> by lazy { buildKodeeMesh() }

private fun buildKodeeMesh(): List<KodeeFace3D> {
  val faces = mutableListOf<KodeeFace3D>()

  fun addBox(x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float) {
    // Front face
    faces.add(
      KodeeFace3D(
        listOf(
          Vec3(x0, y1, z0), Vec3(x1, y1, z0), Vec3(x1, y0, z0), Vec3(x0, y0, z0)
        ), KodeePurpleLight
      )
    )
    // Back face
    faces.add(
      KodeeFace3D(
        listOf(
          Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x0, y1, z1)
        ), KodeePurpleDark
      )
    )
    // Left face
    faces.add(
      KodeeFace3D(
        listOf(
          Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x0, y1, z0), Vec3(x0, y0, z0)
        ), KodeePurple
      )
    )
    // Right face
    faces.add(
      KodeeFace3D(
        listOf(
          Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1), Vec3(x1, y0, z1)
        ), KodeePurple
      )
    )
    // Top face
    faces.add(
      KodeeFace3D(
        listOf(
          Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0), Vec3(x0, y1, z0)
        ), KodeePurple
      )
    )
    // Bottom face
    faces.add(
      KodeeFace3D(
        listOf(
          Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(x0, y0, z1)
        ), KodeePurpleDark
      )
    )
  }

  val zF = -0.25f
  val zB = 0.25f
  val bodyK = listOf(
    0.45f to -0.47f,  // 0: bottom-right
    -0.45f to -0.47f,  // 1: bottom-left
    -0.58f to -0.34f,  // 2: lower-left
    -0.58f to 0.37f,  // 3: left-ear base
    -0.35f to 0.48f,  // 4: left-ear tip
    -0.03f to 0.24f,  // 5: left notch
    0.03f to 0.24f,  // 6: right notch
    0.35f to 0.48f,  // 7: right-ear tip
    0.58f to 0.37f,  // 8: right-ear base
    0.58f to -0.34f,  // 9: lower-right
  )

  faces.add(KodeeFace3D(bodyK.map { (x, y) -> Vec3(x, y, zF) }, KodeePurpleLight))
  faces.add(KodeeFace3D(bodyK.asReversed().map { (x, y) -> Vec3(x, y, zB) }, KodeePurpleDark))

  for (i in bodyK.indices) {
    val j = (i + 1) % bodyK.size
    val (ax, ay) = bodyK[i]; val (bx, by) = bodyK[j]
    faces.add(
      KodeeFace3D(
        listOf(
          Vec3(bx, by, zB), Vec3(bx, by, zF),
          Vec3(ax, ay, zF), Vec3(ax, ay, zB)
        ), KodeePurple
      )
    )
  }

  faces.add(
    KodeeFace3D(
      listOf(
        Vec3(-0.43f, -0.40f, -0.26f),
        Vec3(0.43f, -0.40f, -0.26f),
        Vec3(0.43f, 0.16f, -0.26f),
        Vec3(-0.43f, 0.16f, -0.26f),
      ), KodeeFace, lit = false, layer = 1, backFaceCull = true, visibleNormalZThreshold = 0.05f
    )
  )

  val eyeOuterR = 0.18f
  val eyeInnerR = 0.09f
  val eyeZ = -0.27f
  val eyeInnerZ = -0.275f
  fun addEye(cx: Float, cy: Float) {
    val outerPts = (0 until 8).map { i ->
      val a = i * 2f * PI.toFloat() / 8f
      Vec3(cx + eyeOuterR * cos(a), cy + eyeOuterR * sin(a), eyeZ)
    }
    faces.add(
      KodeeFace3D(
        outerPts,
        Color.White,
        lit = false,
        layer = 2,
        backFaceCull = true,
        visibleNormalZThreshold = 0.05f
      )
    )
    val innerPts = (0 until 8).map { i ->
      val a = i * 2f * PI.toFloat() / 8f
      Vec3(cx + eyeInnerR * cos(a), cy + eyeInnerR * sin(a), eyeInnerZ)
    }
    faces.add(
      KodeeFace3D(
        innerPts,
        KodeeFace,
        lit = false,
        layer = 3,
        backFaceCull = true,
        visibleNormalZThreshold = 0.05f
      )
    )
  }
  addEye(-0.21f, -0.10f)
  addEye(0.21f, -0.10f)

  // Arms
  addBox(-0.70f, -0.55f, -0.10f, 0.05f, zF, zB)  // left arm
  addBox(0.55f, 0.70f, -0.10f, 0.05f, zF, zB)  // right arm

  // Legs
  addBox(-0.25f, -0.05f, -0.80f, -0.46f, zF, zB)  // left leg
  addBox(0.05f, 0.25f, -0.80f, -0.46f, zF, zB)  // right leg

  return faces
}

private fun DrawScope.drawLimb(
  startX: Float, startY: Float,
  endX: Float, endY: Float,
  maxLength: Float,
  thickness: Float,
  color: Color,
  arcDir: Float,
  path: Path,
  footRadius: Float = 0f,
  footDir: Float = 0f,
) {
  val dx = endX - startX
  val dy = endY - startY
  val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.1f)
  val chord = dist.coerceAtMost(maxLength)
  val scale = chord / dist
  val aEndX = startX + dx * scale
  val aEndY = startY + dy * scale

  val midX = (startX + aEndX) / 2f
  val midY = (startY + aEndY) / 2f
  val arcAmount = (maxLength - chord) * arcDir * 0.5f
  val ctrlX = midX + (-(aEndY - startY) / chord.coerceAtLeast(0.1f)) * arcAmount
  val ctrlY = midY + ((aEndX - startX) / chord.coerceAtLeast(0.1f)) * arcAmount

  path.reset()
  path.moveTo(startX, startY)
  path.quadraticTo(ctrlX, ctrlY, aEndX, aEndY)
  if (footRadius > 0f) {
    path.lineTo(aEndX + footDir * footRadius * 2f, aEndY)
  }
  drawPath(path, color, style = Stroke(width = thickness, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawKodee2D(
  path: Path,
  cx: Float, cy: Float, bodySize: Float,
  leftPalmWorldPos: Offset, rightPalmWorldPos: Offset,
  leftFootWorldPos: Offset, rightFootWorldPos: Offset,
  leftArmArcDir: Float = 1f, rightArmArcDir: Float = -1f,
  leftLegArcDir: Float = 1f, rightLegArcDir: Float = -1f,
  bodyRotation: Float = 0f,
  headRotation: Float = 0f,
  leftEyeWinkProgress: Float = 0f, rightEyeWinkProgress: Float = 0f,
  eyeWinkDir: Float = 0f,
  mouthCurveDir: Float = -1f,
  mouthCurveProgress: Float = 1f,
  mouthSizeMultiplier: Float = 1f,
) {
  val scale = bodySize / 76f

  val armLength = 0.70f * bodySize
  val armThickness = 0.12f * bodySize
  val legLength = 0.75f * bodySize
  val legThickness = 0.13f * bodySize
  val footRadius = 4.6f * scale
  fun sx(x: Float) = cx + (x - 70.5f) * scale
  fun sy(y: Float) = cy + (y - 52f) * scale

  fun Offset.bodyLocal(): Offset {
    if (bodyRotation == 0f) return this
    val rad = -bodyRotation * (PI.toFloat() / 180f)
    val cosAngle = cos(rad)
    val sinAngle = sin(rad)
    val dx = x - cx
    val dy = y - cy
    return Offset(cx + dx * cosAngle - dy * sinAngle, cy + dx * sinAngle + dy * cosAngle)
  }

  val shoulderLX = sx(34.2f)
  val shoulderLY = sy(62.5f)
  val shoulderRX = sx(106.6f)
  val shoulderRY = sy(62.5f)
  val hipLX = sx(62.0f)
  val hipLY = sy(86.0f)
  val hipRX = sx(79.0f)
  val hipRY = sy(86.0f)
  val leftPalm = leftPalmWorldPos.bodyLocal()
  val rightPalm = rightPalmWorldPos.bodyLocal()
  val leftFoot = leftFootWorldPos.bodyLocal()
  val rightFoot = rightFootWorldPos.bodyLocal()

  withTransform({ rotate(bodyRotation, Offset(cx, cy)) }) {

    // 1. Arms (behind body)
    drawLimb(
      shoulderLX,
      shoulderLY,
      leftPalm.x,
      leftPalm.y,
      armLength,
      armThickness,
      KodeePurple,
      arcDir = leftArmArcDir,
      path = path
    )
    drawLimb(
      shoulderRX,
      shoulderRY,
      rightPalm.x,
      rightPalm.y,
      armLength,
      armThickness,
      KodeePurple,
      arcDir = rightArmArcDir,
      path = path
    )

    // 2. Legs (behind body)
    drawLimb(
      hipLX,
      hipLY,
      leftFoot.x,
      leftFoot.y,
      legLength,
      legThickness,
      KodeePurple,
      arcDir = leftLegArcDir,
      path = path,
      footRadius = footRadius,
      footDir = -1f
    )
    drawLimb(
      hipRX,
      hipRY,
      rightFoot.x,
      rightFoot.y,
      legLength,
      legThickness,
      KodeePurple,
      arcDir = rightLegArcDir,
      path = path,
      footRadius = footRadius,
      footDir = 1f
    )

    // 3. K-body
    path.reset()
    path.moveTo(sx(36.4f), sy(87.5f))
    path.lineTo(sx(104.8f), sy(87.5f))
    path.cubicTo(sx(110.1f), sy(87.5f), sx(114.4f), sy(83.1f), sx(114.5f), sy(77.8f))
    path.lineTo(sx(114.5f), sy(23.7f))
    path.cubicTo(sx(114.5f), sy(15.1f), sx(104.6f), sy(10.1f), sx(97.6f), sy(15.3f))
    path.lineTo(sx(73.0f), sy(34.0f))
    path.cubicTo(sx(71.6f), sy(35.0f), sx(69.7f), sy(35.1f), sx(68.3f), sy(34.0f))
    path.lineTo(sx(43.6f), sy(15.2f))
    path.cubicTo(sx(36.6f), sy(10.0f), sx(26.8f), sy(14.9f), sx(26.8f), sy(23.7f))
    path.lineTo(sx(26.8f), sy(77.7f))
    path.cubicTo(sx(26.8f), sy(83.1f), sx(31.1f), sy(87.4f), sx(36.4f), sy(87.5f))
    path.close()
    drawPath(path, KodeePurple)

    // 4. Face panel, eyes, mouth
    val eyeRadius = 11.0f * scale
    val eyeStroke = Stroke(width = 4.8f * scale)
    val leftEye = Offset(sx(54.9f), sy(59.4f))
    val rightEye = Offset(sx(86.4f), sy(59.4f))
    val facePivot = Offset(sx(71.4f), sy(61.1f))
    withTransform({ rotate(headRotation, facePivot) }) {
      path.reset()
      path.moveTo(sx(103.5f), sy(82.7f))
      path.lineTo(sx(37.7f), sy(82.7f))
      path.cubicTo(sx(34.4f), sy(82.6f), sx(31.6f), sy(80.0f), sx(31.6f), sy(76.6f))
      path.lineTo(sx(31.6f), sy(45.9f))
      path.cubicTo(sx(31.7f), sy(42.6f), sx(34.4f), sy(39.8f), sx(37.7f), sy(39.8f))
      path.lineTo(sx(103.5f), sy(39.8f))
      path.cubicTo(sx(106.8f), sy(39.8f), sx(109.6f), sy(42.5f), sx(109.6f), sy(45.8f))
      path.lineTo(sx(109.6f), sy(76.6f))
      path.cubicTo(sx(109.6f), sy(79.9f), sx(107.0f), sy(82.6f), sx(103.5f), sy(82.7f))
      path.close()
      drawPath(path, KodeeFace)

      // 5. Eyes
      fun drawEye(center: Offset, winkProgress: Float) {
        val ry = eyeRadius * (1f - winkProgress).coerceAtLeast(0f)
        val shiftedCenterY = center.y - eyeRadius * winkProgress * eyeWinkDir
        if (winkProgress < 0.8f) {
          drawOval(
            Color.White,
            topLeft = Offset(center.x - eyeRadius, shiftedCenterY - ry),
            size = Size(eyeRadius * 2f, ry * 2f), style = eyeStroke
          )
        }
        else {
          val baseT = ((winkProgress - 0.8f) / 0.2f).coerceIn(0f, 1f)
          val endpointY = shiftedCenterY + eyeRadius * eyeWinkDir * baseT * 0.5f
          val topCtrlY = endpointY - 2f * ry - eyeRadius * eyeWinkDir * baseT
          val botCtrlY = (endpointY + 2f * ry) + (topCtrlY - endpointY - 2f * ry) * baseT
          val arcStroke = Stroke(width = eyeStroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
          path.reset()
          path.moveTo(center.x - eyeRadius, endpointY)
          path.quadraticTo(center.x, topCtrlY, center.x + eyeRadius, endpointY)
          drawPath(path, Color.White, style = arcStroke)
          path.reset()
          path.moveTo(center.x + eyeRadius, endpointY)
          path.quadraticTo(center.x, botCtrlY, center.x - eyeRadius, endpointY)
          drawPath(path, Color.White, style = arcStroke)
        }
      }
      drawEye(leftEye, leftEyeWinkProgress)
      drawEye(rightEye, rightEyeWinkProgress)

      // 6. Mouth
      val mouthCenterX = 70f
      val mouthCenterY = 74f
      val mouthStartDx = (65f - mouthCenterX) * mouthSizeMultiplier
      val mouthStartDy = (74f - mouthCenterY) * mouthSizeMultiplier
      val mouthEndDx = (76f - mouthCenterX) * mouthSizeMultiplier
      val mouthEndDy = (74f - mouthCenterY) * mouthSizeMultiplier
      val mouthCurveDx = (70f - mouthCenterX) * mouthSizeMultiplier
      val mouthCurveDy = (78f - mouthCenterY) * mouthSizeMultiplier
      val mouthStartX = sx(mouthCenterX + mouthStartDx)
      val mouthStartY = sy(mouthCenterY + mouthStartDy)
      val mouthEndX = sx(mouthCenterX + mouthEndDx)
      val mouthEndY = sy(mouthCenterY + mouthEndDy)
      val neutralCtrlX = (mouthStartX + mouthEndX) / 2f
      val neutralCtrlY = (mouthStartY + mouthEndY) / 2f
      val mouthCurveFactor = -mouthCurveDir
      val closedMouthCtrlX = neutralCtrlX + (sx(mouthCenterX + mouthCurveDx) - sx(mouthCenterX)) * mouthCurveFactor
      val closedMouthCtrlY = neutralCtrlY + (sy(mouthCenterY + mouthCurveDy) - sy(mouthCenterY)) * mouthCurveFactor
      val mouthHalfWidth = (mouthEndX - mouthStartX) / 2f
      val mouthStroke = Stroke(width = 2.0f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
      val mouthOpenRadiusY = mouthHalfWidth * (1f - mouthCurveProgress).coerceAtLeast(0f)
      if (mouthCurveProgress < 0.8f) {
        drawOval(
          Color.White,
          topLeft = Offset(neutralCtrlX - mouthHalfWidth, neutralCtrlY - mouthOpenRadiusY),
          size = Size(mouthHalfWidth * 2f, mouthOpenRadiusY * 2f),
          style = mouthStroke,
        )
      }
      else {
        val baseT = ((mouthCurveProgress - 0.8f) / 0.2f).coerceIn(0f, 1f)
        val openTopCtrlY = neutralCtrlY - 2f * mouthOpenRadiusY
        val openBottomCtrlY = neutralCtrlY + 2f * mouthOpenRadiusY
        val topStartX = lerp(neutralCtrlX - mouthHalfWidth, mouthStartX, baseT)
        val topStartY = lerp(neutralCtrlY, mouthStartY, baseT)
        val topEndX = lerp(neutralCtrlX + mouthHalfWidth, mouthEndX, baseT)
        val topEndY = lerp(neutralCtrlY, mouthEndY, baseT)
        val topCtrlX = lerp(neutralCtrlX, closedMouthCtrlX, baseT)
        val topCtrlY = lerp(openTopCtrlY, closedMouthCtrlY, baseT)
        val bottomStartX = lerp(neutralCtrlX + mouthHalfWidth, mouthEndX, baseT)
        val bottomStartY = lerp(neutralCtrlY, mouthEndY, baseT)
        val bottomEndX = lerp(neutralCtrlX - mouthHalfWidth, mouthStartX, baseT)
        val bottomEndY = lerp(neutralCtrlY, mouthStartY, baseT)
        val bottomCtrlX = lerp(neutralCtrlX, closedMouthCtrlX, baseT)
        val bottomCtrlY = lerp(openBottomCtrlY, closedMouthCtrlY, baseT)

        path.reset()
        path.moveTo(topStartX, topStartY)
        path.quadraticTo(topCtrlX, topCtrlY, topEndX, topEndY)
        drawPath(path, Color.White, style = mouthStroke)

        path.reset()
        path.moveTo(bottomStartX, bottomStartY)
        path.quadraticTo(bottomCtrlX, bottomCtrlY, bottomEndX, bottomEndY)
        drawPath(path, Color.White, style = mouthStroke)
      }
    }

  }
}

/**
 * Draws Kodee in a 2-D pose inside a [Canvas], deriving the body center and size from the available space.
 * Arms and legs are rendered behind the K-shaped body fill, so limb targets that sit too close to the body center
 * can be partially or fully occluded by the torso. In practice this body-vs-limb overlap is often visually subtle,
 * because both the torso and limbs use the same purple fill. Face-vs-limb overlap is much more noticeable because
 * the face panel uses a contrasting fill, so expressive arm poses usually need palm targets pushed outward from the
 * centerline and away from the face area rather than tightly clustered near `x = 0`.
 *
 * @param bodySizeFraction fraction of the smaller canvas dimension used as the body size.
 * @param bodyOffset offset of the body center from the canvas center, expressed in body-size units.
 * @param leftPalmWorldPos canvas-space [Offset] for the left palm target, expressed relative to the canvas center in body-size units.
 * Targets near the torso center can disappear into the torso silhouette.
 * @param rightPalmWorldPos canvas-space [Offset] for the right palm target, expressed relative to the canvas center in body-size units.
 * Targets near the torso center can disappear into the torso silhouette.
 * @param leftFootWorldPos canvas-space [Offset] for the left foot target, expressed relative to the canvas center in body-size units.
 * @param rightFootWorldPos canvas-space [Offset] for the right foot target, expressed relative to the canvas center in body-size units.
 * @param leftArmArcDir bend direction for the left arm: `+1` bows outward (default), `-1` inward, `0` straight.
 * @param rightArmArcDir bend direction for the right arm: `-1` bows outward, `+1` inward (default), `0` straight.
 * @param leftLegArcDir bend direction for the left leg (same convention); default `0` is straight.
 * @param rightLegArcDir bend direction for the right leg (same convention); default `0` is straight.
 * @param bodyRotation clockwise rotation of the whole body in degrees (default `0`).
 * @param headRotation clockwise head rotation in degrees around the face pivot, applied on top of [bodyRotation] (default `0`).
 * @param leftEyeWinkProgress wink animation progress for the left eye; coerced to `0..1`, where `0` is fully open and `1` is fully closed. Defaults to `0`.
 * @param rightEyeWinkProgress wink animation progress for the right eye; coerced to `0..1`, where `0` is fully open and `1` is fully closed. Defaults to `0`.
 * @param eyeWinkDir arc direction applied to both eyes when winking; coerced to `-1..1`: `0` squishes the eye to a line, `+1` leaves a top arc, and `-1` leaves a bottom arc. Defaults to `0`.
 * @param mouthCurveDir arc direction applied to the mouth; `-1` is the subtle smile, `0` is neutral, and `+1` is a subtle frown. Defaults to `-1`.
 * @param mouthCurveProgress mouth open/close amount; coerced to `0..1`, where `0` is a fully open `O` shape and `1` is fully closed. Defaults to `1`.
 * @param mouthSizeMultiplier scales the mouth size; values below `1` shrink it, values above `1` enlarge it, and the value is coerced to at least `0`. Defaults to `1`.
 */
@Composable
fun Kodee2D(
  modifier: Modifier = Modifier,
  bodySizeFraction: Float = 0.55f,
  bodyOffset: Offset = Offset.Zero,
  leftPalmWorldPos: Offset = Offset(-0.8f, 0.6f),
  rightPalmWorldPos: Offset = Offset(0.8f, 0.6f),
  leftFootWorldPos: Offset = Offset(-0.25f, 1.14f),
  rightFootWorldPos: Offset = Offset(0.25f, 1.14f),
  leftArmArcDir: Float = 1f,
  rightArmArcDir: Float = -1f,
  leftLegArcDir: Float = 1f,
  rightLegArcDir: Float = -1f,
  bodyRotation: Float = 0f,
  headRotation: Float = 0f,
  leftEyeWinkProgress: Float = 0f,
  rightEyeWinkProgress: Float = 0f,
  eyeWinkDir: Float = 0f,
  mouthCurveDir: Float = -1f,
  mouthCurveProgress: Float = 1f,
  mouthSizeMultiplier: Float = 1f,
) {
  val path = remember { Path() }
  val clampedLeftEyeWinkProgress = leftEyeWinkProgress.coerceIn(0f, 1f)
  val clampedRightEyeWinkProgress = rightEyeWinkProgress.coerceIn(0f, 1f)
  val clampedEyeWinkDir = eyeWinkDir.coerceIn(-1f, 1f)
  val clampedMouthCurveProgress = mouthCurveProgress.coerceIn(0f, 1f)
  val clampedMouthSizeMultiplier = mouthSizeMultiplier.coerceAtLeast(0f)
  Canvas(modifier = modifier) {
    val canvasCx = size.width / 2f
    val canvasCy = size.height / 2f
    val bodySize = minOf(size.width, size.height) * bodySizeFraction.coerceAtLeast(0f)
    val cx = canvasCx + bodyOffset.x * bodySize
    val cy = canvasCy + bodyOffset.y * bodySize
    drawKodee2D(
      path = path,
      cx = cx, cy = cy, bodySize = bodySize,
      leftPalmWorldPos = Offset(
        canvasCx + leftPalmWorldPos.x * bodySize,
        canvasCy + leftPalmWorldPos.y * bodySize,
      ),
      rightPalmWorldPos = Offset(
        canvasCx + rightPalmWorldPos.x * bodySize,
        canvasCy + rightPalmWorldPos.y * bodySize,
      ),
      leftFootWorldPos = Offset(
        canvasCx + leftFootWorldPos.x * bodySize,
        canvasCy + leftFootWorldPos.y * bodySize,
      ),
      rightFootWorldPos = Offset(
        canvasCx + rightFootWorldPos.x * bodySize,
        canvasCy + rightFootWorldPos.y * bodySize,
      ),
      leftArmArcDir = leftArmArcDir,
      rightArmArcDir = rightArmArcDir,
      leftLegArcDir = leftLegArcDir,
      rightLegArcDir = rightLegArcDir,
      bodyRotation = bodyRotation,
      headRotation = headRotation,
      leftEyeWinkProgress = clampedLeftEyeWinkProgress,
      rightEyeWinkProgress = clampedRightEyeWinkProgress,
      eyeWinkDir = clampedEyeWinkDir,
      mouthCurveDir = mouthCurveDir,
      mouthCurveProgress = clampedMouthCurveProgress,
      mouthSizeMultiplier = clampedMouthSizeMultiplier,
    )
  }
}

/**
 * Renders the low-poly 3D Kodee model with explicit rotation and translation controls.
 *
 * Rotation values are Euler angles in radians and are applied in `X -> Y -> Z` order.
 * A full rotation is `2 * PI`.
 *
 * Translation is applied in 3D space before projection:
 * `positionX` moves the model left/right, `positionY` moves it up/down, and `positionZ`
 * moves it toward/away from the camera, which also affects its apparent scale.
 */
@Composable
fun Kodee3D(
  modifier: Modifier = Modifier,
  rotationX: Float = 0f,
  rotationY: Float = 0f,
  rotationZ: Float = 0f,
  positionX: Float = 0f,
  positionY: Float = 0f,
  positionZ: Float = 0f,
) {
  val rotBuf = remember { FloatArray(KODEE_MESH.maxOf { it.verts.size } * 3) }
  val facePool = remember { KODEE_MESH.map { f -> RenderedFace(FloatArray(f.verts.size * 2), f.verts.size) } }
  val faces = remember { ArrayList<RenderedFace>(KODEE_MESH.size) }
  val renderPath = remember { Path() }

  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val scale = minOf(w, h) * KODEE_SCALE_FACTOR

    val sinX = sin(rotationX)
    val cosX = cos(rotationX)
    val sinY = sin(rotationY)
    val cosY = cos(rotationY)
    val sinZ = sin(rotationZ)
    val cosZ = cos(rotationZ)
    faces.clear()

    for ((meshIdx, face) in KODEE_MESH.withIndex()) {
      val verts = face.verts
      val n = verts.size

      for (i in 0 until n) {
        val v = verts[i]
        val y1 = v.y * cosX - v.z * sinX
        val z1 = v.y * sinX + v.z * cosX

        val x2 = v.x * cosY + z1 * sinY
        val z2 = -v.x * sinY + z1 * cosY

        val x3 = x2 * cosZ - y1 * sinZ + positionX
        val y3 = x2 * sinZ + y1 * cosZ + positionY
        val z3 = z2 + positionZ

        rotBuf[i * 3] = x3
        rotBuf[i * 3 + 1] = y3
        rotBuf[i * 3 + 2] = z3
      }

      val v0x = rotBuf[0]
      val v0y = rotBuf[1]
      val v0z = rotBuf[2]
      val v1x = rotBuf[3]
      val v1y = rotBuf[4]
      val v1z = rotBuf[5]
      val v2x = rotBuf[6]
      val v2y = rotBuf[7]
      val v2z = rotBuf[8]
      val e1x = v1x - v0x
      val e1y = v1y - v0y
      val e1z = v1z - v0z
      val e2x = v2x - v0x
      val e2y = v2y - v0y
      val e2z = v2z - v0z
      val nx = e1y * e2z - e1z * e2y
      val ny = e1z * e2x - e1x * e2z
      val nz = e1x * e2y - e1y * e2x
      val nLen = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
      val nnz = nz / nLen

      if (face.backFaceCull && nnz <= face.visibleNormalZThreshold) continue

      var avgZSum = 0f
      for (i in 0 until n) avgZSum += rotBuf[i * 3 + 2]
      val avgZ = avgZSum / n

      val color: Color
      if (!face.lit) {
        color = face.baseColor
      }
      else {
        val nny = ny / nLen

        val diffuse = (nny * KODEE_LIGHT_Y + nnz * KODEE_LIGHT_Z).coerceAtLeast(0f)
        val brightness = (KODEE_AMBIENT + KODEE_DIFFUSE_STRENGTH * diffuse).coerceIn(0f, 1f)
        color = lerp(KodeePurpleDark, face.baseColor, brightness)
      }

      val rf = facePool[meshIdx]
      val scrPts = rf.screenPts
      for (i in 0 until n) {
        val rx = rotBuf[i * 3]
        val ry = rotBuf[i * 3 + 1]
        val rz = rotBuf[i * 3 + 2]
        val proj = KODEE_FOV / (KODEE_FOV + rz)
        scrPts[i * 2] = cx + rx * scale * proj
        scrPts[i * 2 + 1] = cy - ry * scale * proj
      }
      rf.avgZ = avgZ
      rf.color = color
      rf.layer = face.layer
      faces.add(rf)
    }

    faces.sortWith(compareBy({ it.layer }, { -it.avgZ }))

    for (face in faces) {
      renderPath.reset()
      renderPath.moveTo(face.screenPts[0], face.screenPts[1])
      for (i in 1 until face.vertCount) {
        renderPath.lineTo(face.screenPts[i * 2], face.screenPts[i * 2 + 1])
      }
      renderPath.close()
      drawPath(renderPath, face.color)
    }
  }
}
