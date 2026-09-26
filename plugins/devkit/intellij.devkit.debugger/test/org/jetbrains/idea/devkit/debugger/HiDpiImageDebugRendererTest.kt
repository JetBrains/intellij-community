// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger

import com.intellij.openapi.util.IconLoader
import com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.IconUtil
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.util.ui.JBImageIcon
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon
import kotlin.math.roundToInt

private const val LOGICAL_SIZE = 8
private val TEST_SCALES = listOf(1.0, 1.25, 1.5, 2.0, 2.5)
private val TEST_HIDPI_SCALES = TEST_SCALES.filter { it > 1.0 }

class HiDpiImageDebugRendererTest : HeavyPlatformTestCase() {
  fun testHiDpiIconRendererMatchesAnySwingIcon() {
    val renderer = HiDpiIconObjectRenderer().createRenderer() as CompoundReferenceRenderer
    assertEquals("HiDPI Icon", renderer.name)
    assertEquals("javax.swing.Icon", renderer.className)
  }

  fun testHelperSerializesPreviewForSmallJBImageIconWithoutBlurringAcrossScales() {
    for (scale in TEST_SCALES) {
      val (icon, expectedRaw) = createTestIcon(scale)
      val preview = iconToBytesPreview(icon, LOGICAL_SIZE)
      assertNotNull("Preview should be available for scale=$scale", preview)

      val data = requireDebugImageData(checkNotNull(preview), "Preview (scale=$scale)")
      val previewIcon = createHiDpiPreviewIcon(data)
      assertNotNull("Preview icon should be reconstructed for scale=$scale", previewIcon)

      assertRoundTripPreview(checkNotNull(previewIcon), expectedRaw, scale)
    }
  }

  fun testPreviewReconstructionUsesPayloadScaleForJBImageIconWhenFrontendScaleDiffers() {
    val debuggeeScale = 2.0
    val frontendScale = 1.0
    val (icon, expectedRaw) = createTestIcon(debuggeeScale)
    val preview = iconToBytesPreview(icon, LOGICAL_SIZE)
    assertNotNull("Preview should be available for scale=$debuggeeScale", preview)

    val data = requireDebugImageData(checkNotNull(preview), "Preview mismatch-scale JBImageIcon")
    val previewIcon = createHiDpiPreviewIcon(data)
    assertNotNull("Preview icon should be reconstructed for mismatched scales", previewIcon)

    assertRoundTripPreviewWithFrontendScale(
      previewIcon = checkNotNull(previewIcon),
      expectedRaw = expectedRaw,
      frontendScale = frontendScale,
      description = "Preview mismatch-scale round-trip (debuggee=$debuggeeScale, frontend=$frontendScale)",
    )
  }

  fun testHelperSerializesPreviewForPlatformCachedIconWithoutBlurringAcrossScales() {
    for (scale in TEST_HIDPI_SCALES) {
      val (icon, expectedRaw) = createPlatformIcon(scale)
      assertFalse("Expected a non-JBImageIcon test icon for scale=$scale", icon is JBImageIcon)

      val maxSize = maxOf(icon.iconWidth, icon.iconHeight)
      val preview = iconToBytesPreview(icon, maxSize)
      assertNotNull("Preview should be available for scale=$scale", preview)

      val data = requireDebugImageData(checkNotNull(preview), "Platform icon preview (scale=$scale)")
      val previewIcon = createHiDpiPreviewIcon(data)
      assertNotNull("Preview icon should be reconstructed for scale=$scale", previewIcon)

      assertRoundTripPreview(checkNotNull(previewIcon), expectedRaw, scale, icon.iconWidth, icon.iconHeight)
    }
  }

  fun testPreviewReconstructionUsesPayloadScaleForPlatformCachedIconWhenFrontendScaleDiffers() {
    val debuggeeScale = 2.0
    val frontendScale = 1.0
    val (icon, expectedRaw) = createPlatformIcon(debuggeeScale)
    assertFalse("Expected a non-JBImageIcon test icon for scale=$debuggeeScale", icon is JBImageIcon)

    val maxSize = maxOf(icon.iconWidth, icon.iconHeight)
    val preview = iconToBytesPreview(icon, maxSize)
    assertNotNull("Preview should be available for scale=$debuggeeScale", preview)

    val data = requireDebugImageData(checkNotNull(preview), "Platform preview mismatch-scale")
    val previewIcon = createHiDpiPreviewIcon(data)
    assertNotNull("Platform preview icon should be reconstructed for mismatched scales", previewIcon)

    assertRoundTripPreviewWithFrontendScale(
      previewIcon = checkNotNull(previewIcon),
      expectedRaw = expectedRaw,
      frontendScale = frontendScale,
      description = "Platform preview mismatch-scale round-trip (debuggee=$debuggeeScale, frontend=$frontendScale)",
      logicalWidth = icon.iconWidth,
      logicalHeight = icon.iconHeight,
    )
  }

  fun testHelperSerializesJBImageIconForPopupAsRawImageAcrossScales() {
    for (scale in TEST_SCALES) {
      val (icon, expectedRaw) = createTestIcon(scale)
      val data = requireDebugImageData(iconToBytes(icon), "Popup icon (scale=$scale)")

      assertRoundTripRawImage(data, expectedRaw, "Popup icon round-trip (scale=$scale)")
    }
  }

  fun testHelperSerializesPlatformCachedIconForPopupAsRawImageAcrossScales() {
    for (scale in TEST_HIDPI_SCALES) {
      val (icon, expectedRaw) = createPlatformIcon(scale)
      assertFalse("Expected a non-JBImageIcon test icon for scale=$scale", icon is JBImageIcon)

      val data = requireDebugImageData(iconToBytes(icon), "Platform popup icon (scale=$scale)")
      assertRoundTripRawImage(data, expectedRaw, "Platform popup icon round-trip (scale=$scale)")
    }
  }

  fun testHelperSerializesJBHiDPIScaledImageAsRawImageAcrossScales() {
    for (scale in TEST_SCALES) {
      val (image, expectedRaw) = createTestImage(scale)
      val data = requireDebugImageData(imageToBytes(image), "Popup image (scale=$scale)")

      assertRoundTripRawImage(data, expectedRaw, "Popup image round-trip (scale=$scale)")
    }
  }

  fun testHelperReturnsNullForOversizedJBImageIconPreview() {
    val (icon, _) = createTestIcon(scale = 2.0, logicalSize = LOGICAL_SIZE + 1)
    assertNull(iconToBytesPreview(icon, LOGICAL_SIZE))
  }

  private fun imageToBytes(image: java.awt.Image): String {
    return invokeSupportMethod(
      IMAGE_TO_BYTES_METHOD,
      arrayOf(java.awt.Image::class.java),
      image,
    ) as String
  }

  private fun iconToBytes(icon: Icon): String {
    return invokeSupportMethod(
      ICON_TO_BYTES_METHOD,
      arrayOf(Icon::class.java),
      icon,
    ) as String
  }

  private fun iconToBytesPreview(icon: Icon, maxSize: Int): String? {
    return invokeSupportMethod(
      ICON_TO_BYTES_PREVIEW_METHOD,
      arrayOf(Icon::class.java, Int::class.javaPrimitiveType!!),
      icon,
      maxSize,
    ) as String?
  }

  private fun invokeSupportMethod(methodName: String, parameterTypes: Array<Class<*>>, vararg args: Any): Any? {
    val clazz = Class.forName(IMAGE_DEBUG_SUPPORT_FQN)
    val method = clazz.getDeclaredMethod(methodName, *parameterTypes)
    method.isAccessible = true
    return method.invoke(null, *args)
  }

  private fun requireDebugImageData(serialized: String, description: String): DebugImageData {
    val data = decodeDebugImageData(serialized)
    assertNotNull("$description should decode", data)
    return checkNotNull(data)
  }

  private fun assertRoundTripPreview(
    previewIcon: ImageIcon,
    expectedRaw: BufferedImage,
    scale: Double,
    logicalWidth: Int = LOGICAL_SIZE,
    logicalHeight: Int = LOGICAL_SIZE,
  ) {
    assertRoundTripPreviewWithFrontendScale(
      previewIcon = previewIcon,
      expectedRaw = expectedRaw,
      frontendScale = scale,
      description = "Preview round-trip (scale=$scale)",
      logicalWidth = logicalWidth,
      logicalHeight = logicalHeight,
    )
  }

  private fun assertRoundTripPreviewWithFrontendScale(
    previewIcon: ImageIcon,
    expectedRaw: BufferedImage,
    frontendScale: Double,
    description: String,
    logicalWidth: Int = LOGICAL_SIZE,
    logicalHeight: Int = LOGICAL_SIZE,
  ) {
    assertEquals("$description logical width mismatch", logicalWidth, previewIcon.iconWidth)
    assertEquals("$description logical height mismatch", logicalHeight, previewIcon.iconHeight)

    val actualRaw = IconUtil.toBufferedImage(previewIcon, scaleContext(frontendScale), false)
    assertImagesEqual(expectedRaw, actualRaw, description)
  }

  private fun assertRoundTripRawImage(data: DebugImageData, expectedRaw: BufferedImage, description: String) {
    val actualRaw = data.toBufferedImage()
    assertNotNull("$description should decode to image", actualRaw)
    assertImagesEqual(expectedRaw, checkNotNull(actualRaw), description)
  }

  private fun assertImagesEqual(expected: BufferedImage, actual: BufferedImage, description: String) {
    assertEquals("$description width mismatch", expected.width, actual.width)
    assertEquals("$description height mismatch", expected.height, actual.height)

    for (y in 0 until expected.height) {
      for (x in 0 until expected.width) {
        val expectedArgb = expected.getRGB(x, y)
        val actualArgb = actual.getRGB(x, y)
        if (expectedArgb != actualArgb) {
          fail(
            "$description pixel mismatch at ($x,$y): expected=${formatArgb(expectedArgb)}, " +
            "actual=${formatArgb(actualArgb)}",
          )
        }
      }
    }
  }

  private fun createTestImage(scale: Double, logicalSize: Int = LOGICAL_SIZE): Pair<JBHiDPIScaledImage, BufferedImage> {
    val rawSize = rawSize(logicalSize, scale)
    val raw = createPatternImage(rawSize, rawSize)
    return JBHiDPIScaledImage(raw, scale) to raw
  }

  private fun createTestIcon(scale: Double, logicalSize: Int = LOGICAL_SIZE): Pair<Icon, BufferedImage> {
    val (image, raw) = createTestImage(scale, logicalSize)
    return JBImageIcon(image) to raw
  }

  private fun createPlatformIcon(scale: Double): Pair<Icon, BufferedImage> {
    val icon = createScaledPlatformIcon(scale)
    return icon to IconUtil.toBufferedImage(icon, false)
  }

  private fun createScaledPlatformIcon(scale: Double): Icon {
    val baseIcon = IconLoader.findIcon("/general/add.svg", IconLoader::class.java.classLoader) as? CachedImageIcon
    assertNotNull("Expected CachedImageIcon test resource", baseIcon)
    return checkNotNull(baseIcon).scale(scaleContext(scale))
  }

  private fun createPatternImage(width: Int, height: Int): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      graphics.color = Color(24, 29, 34)
      graphics.fillRect(0, 0, width, height)

      graphics.color = Color(255, 0, 255)
      graphics.fillRect(0, 0, width / 2, height / 2)

      graphics.color = Color(0, 174, 239)
      graphics.fillRect(width * 2 / 3, height * 2 / 3, width / 3, height / 3)

      graphics.color = Color.WHITE
      graphics.fillRect(width / 2, 0, 1, height)

      graphics.color = Color.BLACK
      graphics.fillRect(0, height / 2 + 1, width, 1)

      graphics.color = Color(255, 127, 39)
      graphics.drawLine(1, height - 2, width - 4, 1)
    }
    finally {
      graphics.dispose()
    }

    image.setRGB(width - 3, 1, 0)
    image.setRGB(width - 2, 1, 0)
    image.setRGB(width - 3, 2, 0)
    image.setRGB(width - 2, 2, 0)

    image.setRGB(0, 0, Color(237, 28, 36).rgb)
    image.setRGB(width - 1, 0, Color(34, 177, 76).rgb)
    image.setRGB(0, height - 1, Color(63, 72, 204).rgb)
    image.setRGB(width - 1, height - 1, Color(255, 201, 14).rgb)
    image.setRGB(width / 2 + 1, 1, Color(255, 127, 39).rgb)
    image.setRGB(1, height / 2 - 1, Color(163, 73, 164).rgb)
    image.setRGB(width - 2, height / 2, Color(112, 146, 190).rgb)

    return image
  }

  private fun rawSize(logicalSize: Int, scale: Double): Int = (logicalSize * scale).roundToInt()

  private fun scaleContext(scale: Double): ScaleContext = ScaleContext.create(ScaleType.SYS_SCALE.of(scale))

  private fun formatArgb(argb: Int): String = "0x%08X".format(argb)
}
