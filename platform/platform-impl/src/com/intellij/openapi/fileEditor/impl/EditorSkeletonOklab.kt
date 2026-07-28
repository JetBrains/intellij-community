// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FloatingPointLiteralPrecision", "UseJBColor")

package com.intellij.openapi.fileEditor.impl

import java.awt.Color
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.withSign

/**
 * Color operations for the editor skeleton that interpolate and adjust colors in Oklab.
 *
 * Oklab is a perceptual color space: equal numeric changes are intended to look more nearly equal to a human observer than changes in
 * RGB. Its three components are `L` (perceived lightness), `a` (green-to-red opponent axis), and `b` (blue-to-yellow opponent axis).
 * This makes Oklab better suited than RGB to producing smooth-looking ramps and predictable lightness shifts.
 *
 * Conversion follows this pipeline:
 *
 * `sRGB -> linear sRGB -> CIE XYZ (D65) -> LMS -> cube root -> Oklab`
 *
 * The reverse conversion applies the inverse matrices and cubes the LMS components. RGB channels outside the sRGB gamut are clipped to
 * `[0, 1]` on output.
 *
 * Glossary:
 * - **XYZ**: the CIE 1931 device-independent tristimulus color space. `Y` represents luminance; `X` and `Z` complete the color description.
 * - **LMS**: a cone-response-like basis whose components correspond approximately to long-, medium-, and short-wavelength sensitivity.
 *   Oklab uses this intermediate space to apply a nonlinear response before forming its opponent axes.
 * - **D65**: the standard white point used by sRGB, representing average daylight with a correlated color temperature of about 6504 K.
 *   The `D65` suffix records the white point assumed by an XYZ conversion; no chromatic adaptation is needed in this pipeline.
 *
 * The matrix constants below are the published Oklab and sRGB conversion transforms, not empirically chosen UI tuning values. Matrices are
 * stored in row-major order: each row calculates one destination component by weighting the three source components. The inverse matrices
 * undo their corresponding forward transforms. Fractional entries in the sRGB/XYZ matrices retain the exact published coefficients rather
 * than rounded decimal approximations.
 *
 * @see <a href="https://bottosson.github.io/posts/oklab/">A perceptual color space for image processing</a>
 */
internal object EditorSkeletonOklab {
  /**
   * Adds [delta] to the Oklab lightness of [color], clamping lightness to `[0, 1]` and preserving alpha.
   *
   * The resulting RGB color is clipped if the adjusted Oklab color lies outside the sRGB gamut.
   */
  fun shiftLightness(color: Color, delta: Double): Color {
    val components = fromRgb(color.getRGBColorComponents(null))
    components[LIGHTNESS_INDEX] = (components[LIGHTNESS_INDEX] + delta).toFloat().coerceIn(MIN_LIGHTNESS, MAX_LIGHTNESS)
    return colorFrom(components, color.alpha)
  }

  /** Creates a color ramp that interpolates Oklab components and alpha between [first] and [second]. */
  fun ramp(first: Color, second: Color): Ramp = Ramp(first, second)

  /** A perceptually smooth Oklab interpolation between two colors. */
  internal class Ramp(private val first: Color, private val second: Color) {
    private val firstComponents = fromRgb(first.getRGBColorComponents(null))
    private val secondComponents = fromRgb(second.getRGBColorComponents(null))

    /**
     * Returns the color at [fraction], where `0.0` is the first color and `1.0` is the second color.
     *
     * Intermediate alpha values are interpolated linearly. RGB output is clipped to the sRGB gamut.
     */
    fun colorAt(fraction: Double): Color {
      require(!fraction.isNaN() && fraction in 0.0..1.0) { "fraction[0..1] is $fraction" }
      if (fraction <= 0.0) return first
      if (fraction >= 1.0) return second

      val components = FloatArray(COMPONENT_COUNT) { index ->
        interpolate(firstComponents[index], secondComponents[index], fraction)
      }
      val alpha = interpolate(first.alpha.toFloat(), second.alpha.toFloat(), fraction).roundToInt()
      return colorFrom(components, alpha)
    }
  }

  private fun toRgb(colorValue: FloatArray): FloatArray {
    require(colorValue.size >= COMPONENT_COUNT)
    val nonlinearLms = multiplyOklabToLms(colorValue)
    val lms = DoubleArray(COMPONENT_COUNT) { index -> nonlinearLms[index].pow(CUBE_EXPONENT) }
    val xyz = multiply3x3(LMS_TO_XYZ_D65, lms)
    val linearRgb = multiply3x3(XYZ_D65_TO_LINEAR_SRGB, xyz)
    return FloatArray(COMPONENT_COUNT) { index ->
      fromLinear(linearRgb[index]).coerceIn(MIN_RGB_COMPONENT, MAX_RGB_COMPONENT).toFloat()
    }
  }

  private fun fromRgb(rgbValue: FloatArray): FloatArray {
    require(rgbValue.size >= COMPONENT_COUNT)
    val linearRgb = DoubleArray(COMPONENT_COUNT) { index -> toLinear(rgbValue[index].toDouble()) }
    val xyz = multiply3x3(LINEAR_SRGB_TO_XYZ_D65, linearRgb)
    val lms = multiply3x3(XYZ_D65_TO_LMS, xyz)
    val nonlinearLms = DoubleArray(COMPONENT_COUNT) { index -> cbrt(lms[index]) }
    return multiply3x3(LMS_TO_OKLAB, nonlinearLms).map(Double::toFloat).toFloatArray()
  }

  private fun colorFrom(components: FloatArray, alpha: Int): Color {
    val rgb = toRgb(components)
    return Color(
      (rgb[0].coerceIn(MIN_COMPONENT_VALUE, MAX_COMPONENT_VALUE) * MAX_CHANNEL_VALUE).roundToInt(),
      (rgb[1].coerceIn(MIN_COMPONENT_VALUE, MAX_COMPONENT_VALUE) * MAX_CHANNEL_VALUE).roundToInt(),
      (rgb[2].coerceIn(MIN_COMPONENT_VALUE, MAX_COMPONENT_VALUE) * MAX_CHANNEL_VALUE).roundToInt(),
      alpha,
    )
  }

  private fun multiplyOklabToLms(vector: FloatArray): DoubleArray {
    return multiply3x3(OKLAB_TO_LMS, DoubleArray(COMPONENT_COUNT) { index -> vector[index].toDouble() })
  }

  private fun multiply3x3(matrix: DoubleArray, vector: DoubleArray): DoubleArray {
    return doubleArrayOf(
      matrix[0] * vector[0] + matrix[1] * vector[1] + matrix[2] * vector[2],
      matrix[3] * vector[0] + matrix[4] * vector[1] + matrix[5] * vector[2],
      matrix[6] * vector[0] + matrix[7] * vector[1] + matrix[8] * vector[2],
    )
  }

  private fun toLinear(value: Double): Double {
    val absolute = abs(value)
    return if (absolute <= SRGB_LINEAR_THRESHOLD) value / LINEAR_SCALE
    else ((absolute + TRANSFER_OFFSET) / TRANSFER_SCALE).pow(TRANSFER_EXPONENT).withSign(value)
  }

  private fun fromLinear(value: Double): Double {
    val absolute = abs(value)
    return if (absolute <= SRGB_GAMMA_THRESHOLD) LINEAR_SCALE * value
    else (TRANSFER_SCALE * absolute.pow(INVERSE_TRANSFER_EXPONENT) - TRANSFER_OFFSET).withSign(value)
  }

  private fun interpolate(first: Float, second: Float, fraction: Double): Float {
    return (first + fraction * (second - first)).toFloat()
  }

  // Revised Oklab transform from D65-relative XYZ to its LMS intermediate space.
  // Rows produce L, M, and S; columns consume X, Y, and Z.
  private val XYZ_D65_TO_LMS = doubleArrayOf(
    0.8190224379967030, 0.3619062600528904, -0.1288737815209879,
    0.0329836539323885, 0.9292868615863434, 0.0361446663506424,
    0.0481771893596242, 0.2642395317527308, 0.6335478284694309,
  )

  // Oklab opponent transform. Rows produce lightness, green-red a, and blue-yellow b from cube-rooted L, M, and S.
  private val LMS_TO_OKLAB = doubleArrayOf(
    0.2104542683093140, 0.7936177747023054, -0.0040720430116193,
    1.9779985324311684, -2.4285922420485799, 0.4505937096174110,
    0.0259040424655478, 0.7827717124575296, -0.8086757549230774,
  )

  // Inverse of XYZ_D65_TO_LMS. Rows produce X, Y, and Z from cubed L, M, and S.
  private val LMS_TO_XYZ_D65 = doubleArrayOf(
    1.2268798758459243, -0.5578149944602171, 0.2813910456659647,
    -0.0405757452148008, 1.1122868032803170, -0.0717110580655164,
    -0.0763729366746601, -0.4214933324022432, 1.5869240198367816,
  )

  // Inverse of LMS_TO_OKLAB. Rows produce cube-rooted L, M, and S from Oklab L, a, and b.
  private val OKLAB_TO_LMS = doubleArrayOf(
    1.0, 0.3963377773761749, 0.2158037573099136,
    1.0, -0.1055613458156586, -0.0638541728258133,
    1.0, -0.0894841775298119, -1.2914855480194092,
  )

  // Standard D65 linear-sRGB-to-XYZ transform. Rows produce X, Y, and Z; columns consume red, green, and blue.
  private val LINEAR_SRGB_TO_XYZ_D65 = doubleArrayOf(
    506752.0 / 1228815.0, 87881.0 / 245763.0, 12673.0 / 70218.0,
    87098.0 / 409605.0, 175762.0 / 245763.0, 12673.0 / 175545.0,
    7918.0 / 409605.0, 87881.0 / 737289.0, 1001167.0 / 1053270.0,
  )

  // Inverse of LINEAR_SRGB_TO_XYZ_D65. Rows produce linear red, green, and blue from X, Y, and Z.
  private val XYZ_D65_TO_LINEAR_SRGB = doubleArrayOf(
    12831.0 / 3959.0, -329.0 / 214.0, -1974.0 / 3959.0,
    -851781.0 / 878810.0, 1648619.0 / 878810.0, 36519.0 / 878810.0,
    705.0 / 12673.0, -2585.0 / 12673.0, 705.0 / 667.0,
  )

  private const val COMPONENT_COUNT = 3
  private const val LIGHTNESS_INDEX = 0
  private const val MIN_LIGHTNESS = 0.0f
  private const val MAX_LIGHTNESS = 1.0f
  private const val CUBE_EXPONENT = 3
  private const val MIN_RGB_COMPONENT = 0.0
  private const val MAX_RGB_COMPONENT = 1.0
  private const val MIN_COMPONENT_VALUE = 0.0f
  private const val MAX_COMPONENT_VALUE = 1.0f
  private const val MAX_CHANNEL_VALUE = 255

  // Constants specified by the piecewise sRGB transfer function that converts between gamma-encoded and linear channel values.
  private const val SRGB_LINEAR_THRESHOLD = 0.04045
  private const val SRGB_GAMMA_THRESHOLD = 0.0031308
  private const val LINEAR_SCALE = 12.92
  private const val TRANSFER_OFFSET = 0.055
  private const val TRANSFER_SCALE = 1.055
  private const val TRANSFER_EXPONENT = 2.4
  private const val INVERSE_TRANSFER_EXPONENT = 1.0 / TRANSFER_EXPONENT
}
