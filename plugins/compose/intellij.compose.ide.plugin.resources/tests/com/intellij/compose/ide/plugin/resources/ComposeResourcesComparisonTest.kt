// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.android.ide.common.util.GeneratorTester
import com.intellij.compose.ide.plugin.resources.android.AndroidDrawablePreviewRenderer
import com.intellij.compose.ide.plugin.resources.vectorDrawable.preview.BaseVectorDrawablePreviewRenderer.RenderResult
import com.intellij.compose.ide.plugin.resources.vectorDrawable.preview.ComposeResourcesDrawablePreviewRenderer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Compares the ComposeResources VectorDrawable renderer against Android's VdPreview implementation.
 */
class ComposeResourcesComparisonTest : BasePlatformTestCase() {

  private val androidRenderer = AndroidDrawablePreviewRenderer()
  private val kotlinRenderer = ComposeResourcesDrawablePreviewRenderer()

  fun `test size opacity`() = compareRendering("ic_size_opacity")

  fun `test tint and opacity`() = compareRendering("test_tint_and_opacity")

  fun `test xml color formats`() = compareRendering("test_xml_color_formats")

  fun `test fill stroke alpha`() = compareRendering("test_fill_stroke_alpha")

  fun `test xml transformation 1`() = compareRendering("test_xml_transformation_1")

  fun `test xml transformation 2`() = compareRendering("test_xml_transformation_2")

  fun `test xml transformation 3`() = compareRendering("test_xml_transformation_3")

  fun `test xml transformation 4`() = compareRendering("test_xml_transformation_4")

  fun `test xml transformation 5`() = compareRendering("test_xml_transformation_5")

  fun `test xml transformation 6`() = compareRendering("test_xml_transformation_6")

  fun `test xml scale stroke 1`() = compareRendering("test_xml_scale_stroke_1")

  fun `test xml scale stroke 2`() = compareRendering("test_xml_scale_stroke_2")

  fun `test xml render order 1`() = compareRendering("test_xml_render_order_1")

  fun `test xml render order 2`() = compareRendering("test_xml_render_order_2")

  fun `test xml repeated a 1`() = compareRendering("test_xml_repeated_a_1")

  fun `test xml repeated a 2`() = compareRendering("test_xml_repeated_a_2")

  fun `test xml repeated cq`() = compareRendering("test_xml_repeated_cq")

  fun `test xml repeated st`() = compareRendering("test_xml_repeated_st")

  fun `test xml stroke 1`() = compareRendering("test_xml_stroke_1")

  fun `test xml stroke 2`() = compareRendering("test_xml_stroke_2")

  fun `test xml stroke 3`() = compareRendering("test_xml_stroke_3")

  fun `test pathData in string tools resource`() = compareRendering("test_pathData_in_string_tools_resource")

  fun `test small image with tint`() = compareRendering("test_small_image_with_tint")

  fun `test pathData in string resource error`() = compareRendering("test_pathData_in_string_resource")

  fun `test legacy arc flags`() = compareRendering("test_legacy_arc_flags")

  fun `test implicit lineTo after moveTo`() = compareRendering("test_implicit_line_to")

  fun `test path without initial moveTo`() = compareRendering("test_path_without_move_to")

  fun `test gradient without aapt attr`() = compareRendering("test_gradient_without_aapt")

  fun `test valid fill gradient`() = compareRendering("test_fill_gradient")

  fun `test valid stroke gradient`() = compareRendering("test_stroke_gradient")

  fun `test radial gradient`() = compareRendering("test_radial_gradient")

  private fun compareRendering(testCase: String) {
    val xml = loadXmlResource(testCase)
    val kotlinResult = kotlinRenderer.renderPreview(xml, IMAGE_SIZE, IMAGE_SIZE)
    val androidResult = androidRenderer.renderPreview(xml, IMAGE_SIZE, IMAGE_SIZE)

    when (androidResult) {
      is RenderResult.Success if kotlinResult is RenderResult.Success -> {
        GeneratorTester.assertImageSimilar(testCase, androidResult.image, kotlinResult.image, IMAGE_DIFF_THRESHOLD)
      }
      is RenderResult.Error if kotlinResult is RenderResult.Error -> {
        assertEquals("Error messages should match for $testCase", androidResult.message, kotlinResult.message)
      }
      else -> {
        fail("Result mismatch for $testCase: Android=${androidResult::class.simpleName}, Kotlin=${kotlinResult::class.simpleName}")
      }
    }
  }

  private fun loadXmlResource(testCase: String): String {
    val fileName = "$testCase.xml"
    return loadLocalTestData(fileName)
           ?: loadClasspathResource("testData/vectordrawable/$fileName")
           ?: error("Test resource not found: $testCase")
  }

  private fun loadLocalTestData(fileName: String): String? {
    val path = "plugins/compose/intellij.compose.ide.plugin.resources/testData/vectordrawable/$fileName"
    val file = File(PlatformTestUtil.getCommunityPath(), path)
    return file.takeIf { it.exists() }?.readText()
  }

  private fun loadClasspathResource(path: String): String? {
    return javaClass.classLoader.getResourceAsStream(path)
      ?.bufferedReader()?.use { it.readText() }
  }

  companion object {
    private const val IMAGE_SIZE = 64
    private const val IMAGE_DIFF_THRESHOLD = 1.25f
  }
}