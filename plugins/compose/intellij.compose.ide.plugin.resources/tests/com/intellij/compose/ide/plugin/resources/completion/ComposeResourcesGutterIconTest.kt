// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.completion

import com.android.ide.common.util.GeneratorTester
import com.android.ide.common.vectordrawable.VdPreview
import com.android.ide.common.vectordrawable.VdPreview.TargetSize
import com.android.tools.idea.rendering.GutterIconFactory
import com.android.utils.XmlUtils
import com.intellij.compose.ide.plugin.resources.vectorDrawable.preview.BaseVectorDrawablePreviewRenderer.RenderResult
import com.intellij.compose.ide.plugin.resources.vectorDrawable.preview.ComposeResourcesDrawablePreviewRenderer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.scale.ScaleContext.Companion.create
import com.intellij.util.IconUtil
import com.intellij.util.IconUtil.createImageIcon
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.Icon
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes
import kotlin.test.assertNotNull as kAssertNotNull

private const val IMAGE_DIFF_THRESHOLD = 1.25f

/** Tests compares [ComposeResourcesGutterIconFactory] icons and [GutterIconFactory]'s icons */
class ComposeResourcesGutterIconTest : BasePlatformTestCase() {

  private val maxWidth get() = JBUI.scale(16)

  fun `test XML produces matching icon`() {
    val path = "plugins/compose/intellij.compose.ide.plugin.resources/testData/vectordrawable/test_fill_gradient.xml"
    val file = File(PlatformTestUtil.getCommunityPath(), path)
    assertTrue("Test image not found: ${file.absolutePath}", file.exists())

    val vf = LocalFileSystem.getInstance().findFileByPath(file.absolutePath)
    kAssertNotNull(vf, "Could not find VirtualFile for: ${file.absolutePath}")

    val gutterIcon = createXmlAndroidGutterIcon(vf, maxWidth)
    kAssertNotNull(gutterIcon, "Gutter icon should not be null")

    val composeIcon = renderXmlComposeIcon(vf, maxWidth)
    kAssertNotNull(composeIcon, "Compose icon should not be null")

    val composeRendered = IconUtil.toBufferedImage(composeIcon)
    val gutterRendered = IconUtil.toBufferedImage(gutterIcon)

    GeneratorTester.assertImageSimilar("testCase", gutterRendered, composeRendered, IMAGE_DIFF_THRESHOLD)
  }

  fun `test PNG produces matching icon`() {
    val testImage = createTestImage(512, 512, Color.RED)
    withTempImageFile(testImage) { vf ->
      assertBitmapIconsMatch(vf)
    }
  }

  fun `test small image not upscaled`() {
    val smallImage = createTestImage(8, 8, Color.RED)
    withTempImageFile(smallImage) { vf ->
      assertBitmapIconsMatch(vf, false)
    }
  }

  fun `test wide image preserves aspect ratio`() {
    val inputWidth = 200
    val inputHeight = 50
    val wideImage = createTestImage(inputWidth, inputHeight, Color.BLUE)
    withTempImageFile(wideImage) { vf ->
      assertBitmapIconsMatch(vf)
    }
  }

  fun `test tall image preserves aspect ratio`() {
    val inputWidth = 50
    val inputHeight = 200
    val tallImage = createTestImage(inputWidth, inputHeight, Color.GREEN)
    withTempImageFile(tallImage) { vf ->
      assertBitmapIconsMatch(vf)
    }
  }

  fun `test image at max size produces icon`() {
    val exactImage = createTestImage(maxWidth, maxWidth, Color.YELLOW)
    withTempImageFile(exactImage) { vf ->
      assertBitmapIconsMatch(vf, false)
    }
  }

  fun `test image slightly over max size is scaled down`() {
    val inputSize = 100
    val overImage = createTestImage(inputSize, inputSize, Color.CYAN)
    withTempImageFile(overImage) { vf ->
      assertBitmapIconsMatch(vf)
    }
  }

  fun `test invalid image returns null`() {
    val tempFile = createTempFile(suffix = ".png")
    try {
      tempFile.writeBytes("not a valid image".toByteArray())
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempFile)?.let { vf ->
        val icon = ComposeResourcesGutterIconFactory.renderBitmapDrawable(vf, maxWidth)
        assertNull("Invalid image should return null", icon)
      }
    }
    finally {
      tempFile.deleteIfExists()
    }
  }

  fun `test empty file returns null`() {
    val tempFile = createTempFile(suffix = ".png")
    try {
      tempFile.writeBytes(ByteArray(0))
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempFile)?.let { vf ->
        val icon = ComposeResourcesGutterIconFactory.renderBitmapDrawable(vf, maxWidth)
        assertNull("Empty file should return null", icon)
      }
    }
    finally {
      tempFile.deleteIfExists()
    }
  }

  fun `test large image scaled down correctly`() {
    val largeImage = createTestImage(1024, 1024, Color.MAGENTA)
    withTempImageFile(largeImage) { vf ->
      assertBitmapIconsMatch(vf)
    }
  }

  private fun assertBitmapIconsMatch(virtualFile: VirtualFile, compareImages: Boolean = true) {
    val composeIcon = ComposeResourcesGutterIconFactory.renderBitmapDrawable(virtualFile, maxWidth)
    kAssertNotNull(composeIcon, "renderBitmapDrawable should produce a non-null icon")

    val gutterIcon = callGutterIconFactoryCreateBitmapIcon(virtualFile, maxWidth, maxWidth)
    kAssertNotNull(gutterIcon, "GutterIconFactory should produce a non-null icon")

    assertEquals("Icon widths should match", gutterIcon.iconWidth, composeIcon.iconWidth)
    assertEquals("Icon heights should match", gutterIcon.iconHeight, composeIcon.iconHeight)

    if (!compareImages) return

    val composeRendered = IconUtil.toBufferedImage(composeIcon)
    val gutterRendered = IconUtil.toBufferedImage(gutterIcon)
    GeneratorTester.assertImageSimilar("testCase", gutterRendered, composeRendered, IMAGE_DIFF_THRESHOLD)
  }

  private fun createTestImage(width: Int, height: Int, color: Color): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.color = color
    g.fillRect(0, 0, width, height)
    g.dispose()
    return image
  }

  private fun withTempImageFile(image: BufferedImage, block: (VirtualFile) -> Unit) {
    val tempFile = createTempFile(suffix = ".png")
    try {
      val bytes = ByteArrayOutputStream().use { baos ->
        ImageIO.write(image, "png", baos)
        baos.toByteArray()
      }
      tempFile.writeBytes(bytes)
      val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempFile)
      kAssertNotNull(vf, "Should find temp file as VirtualFile")
      block(vf)
    }
    finally {
      tempFile.deleteIfExists()
    }
  }

  /** [ComposeResourcesGutterIconFactory.renderDrawableIcon] for XML files using the Compose Resources renderer directly */
  private fun renderXmlComposeIcon(file: VirtualFile, maxSize: Int): Icon? {
    val pixelSize = JBUI.pixScale(maxSize.toFloat()).toInt()
    val xmlContent = String(file.contentsToByteArray(), Charsets.UTF_8)
    val renderer = ComposeResourcesDrawablePreviewRenderer()
    val result = renderer.renderPreview(xmlContent, pixelSize, pixelSize)
    val bufferedImage = (result as? RenderResult.Success)?.image ?: return null
    val image = ImageUtil.ensureHiDPI(bufferedImage, create())
    return createImageIcon(image)
  }

  /** Part of [GutterIconFactory.createIcon] for bitmap files via `createBitmapIcon(VirtualFile, int, int)` */
  private fun callGutterIconFactoryCreateBitmapIcon(file: VirtualFile, maxWidth: Int, maxHeight: Int): Icon? {
    val method = GutterIconFactory::class.java.getDeclaredMethod(
      "createBitmapIcon",
      VirtualFile::class.java,
      Int::class.javaPrimitiveType,
      Int::class.javaPrimitiveType,
    )
    method.isAccessible = true
    return method.invoke(null, file, maxWidth, maxHeight) as Icon?
  }

  /** Part of [GutterIconFactory.createIcon] for XML vector-drawables, not used dirrectly, since it requires AndroidFacet */
  private fun createXmlAndroidGutterIcon(file: VirtualFile, maxSize: Int): Icon? = try {
    val doc = FileDocumentManager.getInstance().getCachedDocument(file)
    val xml = doc?.text ?: String(file.contentsToByteArray())
    if (!xml.contains("<vector")) return null

    val pixelSize = JBUI.pixScale(maxSize.toFloat()).toInt()
    val imageTargetSize = TargetSize.createFromMaxDimension(pixelSize)
    val document = XmlUtils.parseDocumentSilently(xml, true) ?: return null
    document.documentElement ?: return null
    val builder = StringBuilder(100)

    val bufferedImage = VdPreview.getPreviewFromVectorDocument(imageTargetSize, document, builder) ?: return null
    val image = ImageUtil.ensureHiDPI(bufferedImage, create())
    return createImageIcon(image)
  }
  catch (_: Throwable) {
    return null
  }
}