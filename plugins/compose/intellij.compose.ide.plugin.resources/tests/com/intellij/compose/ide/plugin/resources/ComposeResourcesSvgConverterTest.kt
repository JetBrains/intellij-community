// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.resources.android.AndroidDrawablePreviewRenderer
import com.intellij.compose.ide.plugin.resources.vectorDrawable.preview.ComposeResourcesDrawablePreviewRenderer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Tests for SVG to VectorDrawable XML conversion.
 */
internal class ComposeResourcesSvgConverterTest : BasePlatformTestCase() {

  private val kotlinRenderer = ComposeResourcesDrawablePreviewRenderer()
  private val androidRenderer = AndroidDrawablePreviewRenderer()

  fun `test svg fill alpha`() = compareRendering("ic_add_to_notepad_black")

  fun `test svg arc to 1`() = compareRendering("test_arcto_1")

  fun `test svg arc to 2`() = compareRendering("test_arcto_2")

  fun `test svg control points 01`() = compareRendering("test_control_points_01")

  fun `test svg control points 02`() = compareRendering("test_control_points_02")

  fun `test svg control points 03`() = compareRendering("test_control_points_03")

  fun `test svg content cut`() = compareRendering("ic_content_cut_24px")

  fun `test svg input`() = compareRendering("ic_input_24px")

  fun `test svg line help`() = compareRendering("ic_live_help_24px")

  fun `test svg local library`() = compareRendering("ic_local_library_24px")

  fun `test svg local phone`() = compareRendering("ic_local_phone_24px")

  fun `test svg mic off`() = compareRendering("ic_mic_off_24px")

  fun `test svg shapes`() = compareRendering("ic_shapes")

  fun `test svg ellipse`() = compareRendering("test_ellipse")

  fun `test svg temp high`() = compareRendering("ic_temp_high")

  fun `test svg plus sign`() = compareRendering("ic_plus_sign")

  fun `test svg polyline stroke width`() = compareRendering("ic_polyline_strokewidth")

  fun `test svg stroke width uniform transform`() = compareRendering("ic_strokewidth_uniform_transform")

  fun `test svg empty attributes`() = compareRendering("ic_empty_attributes")

  fun `test svg empty path data`() = compareRendering("ic_empty_path_data")

  fun `test svg simple group info`() = compareRendering("ic_simple_group_info")

  fun `test svg line to move to`() = compareRendering("test_lineto_moveto")

  fun `test svg line to move to 2`() = compareRendering("test_lineto_moveto2")

  fun `test svg line to move to view box 1`() = compareRendering("test_lineto_moveto_viewbox1")

  fun `test svg line to move to view box 2`() = compareRendering("test_lineto_moveto_viewbox2")

  fun `test svg line to move to view box 3`() = compareRendering("test_lineto_moveto_viewbox3")

  fun `test svg line to move to view box 4`() = compareRendering("test_lineto_moveto_viewbox4")

  fun `test svg line to move to view box 5`() = compareRendering("test_lineto_moveto_viewbox5")

  fun `test svg implicit line to after move to`() = compareRendering("test_implicit_lineto_after_moveto")

  fun `test svg round rect percentage`() = compareRendering("test_round_rect_percentage")

  fun `test svg color formats`() = compareRendering("test_color_formats")

  fun `test svg paint order`() = compareRendering("test_paint_order")

  fun `test svg transform arc complex 1`() = compareRendering("test_transform_arc_complex1")

  fun `test svg transform arc complex 2`() = compareRendering("test_transform_arc_complex2")

  fun `test svg transform arc rotate scale translate`() = compareRendering("test_transform_arc_rotate_scale_translate")

  fun `test svg transform arc scale`() = compareRendering("test_transform_arc_scale")

  fun `test svg transform arc scale rotate`() = compareRendering("test_transform_arc_scale_rotate")

  fun `test svg transform arc skewx`() = compareRendering("test_transform_arc_skewx")

  fun `test svg transform arc skewy`() = compareRendering("test_transform_arc_skewy")

  fun `test svg transform big arc complex`() = compareRendering("test_transform_big_arc_complex")

  fun `test svg transform big arc complex viewbox`() = compareRendering("test_transform_big_arc_complex_viewbox")

  fun `test svg transform big arc scale`() = compareRendering("test_transform_big_arc_translate_scale")

  fun `test svg transform degenerate arc`() = compareRendering("test_transform_degenerate_arc")

  fun `test svg arc without separator between flags`() = compareRendering("test_arc_without_separator_between_flags")

  fun `test svg transform circle rotate`() = compareRendering("test_transform_circle_rotate")

  fun `test svg transform circle scale`() = compareRendering("test_transform_circle_scale")

  fun `test svg transform circle matrix`() = compareRendering("test_transform_circle_matrix")

  fun `test svg transform rect matrix`() = compareRendering("test_transform_rect_matrix")

  fun `test svg transform round rect matrix`() = compareRendering("test_transform_round_rect_matrix")

  fun `test svg transform rect rotate`() = compareRendering("test_transform_rect_rotate")

  fun `test svg transform rect scale`() = compareRendering("test_transform_rect_scale")

  fun `test svg transform rect skewx`() = compareRendering("test_transform_rect_skewx")

  fun `test svg transform rect skewy`() = compareRendering("test_transform_rect_skewy")

  fun `test svg transform rect translate`() = compareRendering("test_transform_rect_translate")

  fun `test svg transform hv loop basic`() = compareRendering("test_transform_h_v_loop_basic")

  fun `test svg transform hv loop translate`() = compareRendering("test_transform_h_v_loop_translate")

  fun `test svg transform hv loop matrix`() = compareRendering("test_transform_h_v_loop_matrix")

  fun `test svg transform hvac complex`() = compareRendering("test_transform_h_v_a_c_complex")

  fun `test svg transform hva complex`() = compareRendering("test_transform_h_v_a_complex")

  fun `test svg transform hvcq`() = compareRendering("test_transform_h_v_c_q")

  fun `test svg transform hvcq complex`() = compareRendering("test_transform_h_v_c_q_complex")

  fun `test svg transform hv loop complex`() = compareRendering("test_transform_h_v_loop_complex")

  fun `test svg transform hvst complex`() = compareRendering("test_transform_h_v_s_t_complex")

  fun `test svg transform hvst complex 2`() = compareRendering("test_transform_h_v_s_t_complex2")

  fun `test svg transform cq no move`() = compareRendering("test_transform_c_q_no_move")

  fun `test svg transform multiple 1`() = compareRendering("test_transform_multiple_1")

  fun `test svg transform multiple 2`() = compareRendering("test_transform_multiple_2")

  fun `test svg transform multiple 3`() = compareRendering("test_transform_multiple_3")

  fun `test svg transform multiple 4`() = compareRendering("test_transform_multiple_4")

  fun `test svg transform group 1`() = compareRendering("test_transform_group_1")

  fun `test svg transform group 2`() = compareRendering("test_transform_group_2")

  fun `test svg transform group 3`() = compareRendering("test_transform_group_3")

  fun `test svg transform group 4`() = compareRendering("test_transform_group_4")

  fun `test svg transform ellipse rotate scale translate`() = compareRendering("test_transform_ellipse_rotate_scale_translate")

  fun `test svg transform ellipse complex`() = compareRendering("test_transform_ellipse_complex")

  fun `test svg move after close 1`() = compareRendering("test_move_after_close1")

  fun `test svg move after close 2`() = compareRendering("test_move_after_close2")

  fun `test svg move after close 3`() = compareRendering("test_move_after_close3")

  fun `test svg move after close transform`() = compareRendering("test_move_after_close_transform")

  fun `test svg fill rule even odd`() = compareRendering("test_fill_type_evenodd")

  fun `test svg fill rule nonzero`() = compareRendering("test_fill_type_nonzero")

  fun `test svg fill rule no rule`() = compareRendering("test_fill_type_no_rule")

  fun `test svg black fill`() = compareRendering("test_black_fill")

  fun `test svg defs use transform`() = compareRendering("test_svg_defs_use_shape2")

  fun `test svg defs use colors`() = compareRendering("test_svg_defs_use_colors")

  fun `test svg defs use no group`() = compareRendering("test_svg_defs_use_no_group")

  fun `test svg defs use nested groups`() = compareRendering("test_defs_use_nested_groups")

  fun `test svg defs use nested groups 2`() = compareRendering("test_svg_defs_use_nested_groups2")

  fun `test svg use without defs`() = compareRendering("test_use_no_defs")

  fun `test svg defs use multi attrib`() = compareRendering("test_svg_defs_use_multi_attr")

  fun `test svg defs use transform rotate`() = compareRendering("test_defs_use_transform")

  fun `test svg defs use transform in defs`() = compareRendering("test_defs_use_transform2")

  fun `test svg defs use order matters`() = compareRendering("test_svg_defs_use_use_first")

  fun `test svg defs use indirect`() = compareRendering("test_defs_use_chain")

  fun `test svg empty attribute`() = compareRendering("test_empty_attribute")

  fun `test svg clip path group`() = compareRendering("test_clip_path_group")

  fun `test svg clip path group 2`() = compareRendering("test_clip_path_group_2")

  fun `test svg clip path translate children`() = compareRendering("test_clip_path_translate_children")

  fun `test svg clip path translate affected`() = compareRendering("test_clip_path_translate_affected")

  fun `test svg clip path is group`() = compareRendering("test_clip_path_is_group")

  fun `test svg clip path multi shape clip`() = compareRendering("test_clip_path_mult_clip")

  fun `test svg clip path over group`() = compareRendering("test_clip_path_over_group")

  fun `test svg clip path rect`() = compareRendering("test_clip_path_rect")

  fun `test svg clip path rect over clip path`() = compareRendering("test_clip_path_rect_over_circle")

  fun `test svg clip path two rect`() = compareRendering("test_clip_path_two_rect")

  fun `test svg clip path single path`() = compareRendering("test_clip_path_path_over_rect")

  fun `test svg clip path ordering`() = compareRendering("test_clip_path_ordering")

  fun `test svg clip even odd`() = compareRendering("test_clip_path_evenodd")

  fun `test svg clip even odd and nonzero`() = compareRendering("test_clip_path_evenodd_and_nonzero")

  fun `test svg clip rule outside of clip path`() = compareRendering("test_clip_rule_outside_of_clippath")

  fun `test svg mask`() = compareRendering("test_mask")

  fun `test svg style basic shapes`() = compareRendering("test_style_basic_shapes")

  fun `test svg style blobfish`() = compareRendering("test_style_blobfish")

  fun `test svg style circle`() = compareRendering("test_style_circle")

  fun `test svg style group`() = compareRendering("test_style_group")

  fun `test svg style group clip path`() = compareRendering("test_style_group_clip_path")

  fun `test svg style group duplicate attr`() = compareRendering("test_style_group_duplicate_attr")

  fun `test svg style multi class`() = compareRendering("test_style_multi_class")

  fun `test svg style two shapes`() = compareRendering("test_style_two_shapes")

  fun `test svg style path class names`() = compareRendering("test_style_path_class_names")

  fun `test svg style short version`() = compareRendering("test_style_short_version")

  fun `test svg style combined`() = compareRendering("test_style_combined")

  fun `test svg gradient linear coordinates negative percentage`() =
    compareRendering("test_gradient_linear_coordinates_negative_percentage")

  fun `test svg gradient linear no coordinates`() = compareRendering("test_gradient_linear_no_coordinates")

  fun `test svg gradient linear no units`() = compareRendering("test_gradient_linear_no_units")

  fun `test svg gradient linear object bounding box`() = compareRendering("test_gradient_linear_object_bounding_box")

  fun `test svg gradient linear offset decreasing`() = compareRendering("test_gradient_linear_offset_decreasing")

  fun `test svg gradient linear offset out of bounds`() = compareRendering("test_gradient_linear_offset_out_of_bounds")

  fun `test svg gradient linear offset undefined`() = compareRendering("test_gradient_linear_offset_undefined")

  fun `test svg gradient linear one stop`() = compareRendering("test_gradient_linear_one_stop")

  fun `test svg gradient linear overlapping stops`() = compareRendering("test_gradient_linear_overlapping_stops")

  fun `test svg gradient linear spread pad`() = compareRendering("test_gradient_linear_spread_pad")

  fun `test svg gradient linear spread reflect`() = compareRendering("test_gradient_linear_spread_reflect")

  fun `test svg gradient linear spread repeat`() = compareRendering("test_gradient_linear_spread_repeat")

  fun `test svg gradient linear stop opacity`() = compareRendering("test_gradient_linear_stop_opacity")

  fun `test svg gradient linear stop opacity half`() = compareRendering("test_gradient_linear_stop_opacity_half")

  fun `test svg gradient linear stroke`() = compareRendering("test_gradient_linear_stroke")

  fun `test svg gradient linear three stops`() = compareRendering("test_gradient_linear_three_stops")

  fun `test svg gradient linear transform group scale translate`() =
    compareRendering("test_gradient_linear_transform_group_scale_translate")

  fun `test svg gradient linear transform matrix 3`() = compareRendering("test_gradient_linear_transform_matrix_3")

  fun `test svg gradient linear transform matrix scale`() = compareRendering("test_gradient_linear_transform_matrix_scale")

  fun `test svg gradient linear transform rotate`() = compareRendering("test_gradient_linear_transform_rotate")

  fun `test svg gradient linear transform rotate scale`() = compareRendering("test_gradient_linear_transform_rotate_scale")

  fun `test svg gradient linear transform rotate translate scale`() =
    compareRendering("test_gradient_linear_transform_rotate_translate_scale")

  fun `test svg gradient linear transform scale`() = compareRendering("test_gradient_linear_transform_scale")

  fun `test svg gradient linear transform translate`() = compareRendering("test_gradient_linear_transform_translate")

  fun `test svg gradient linear transform translate rotate`() = compareRendering("test_gradient_linear_transform_translate_rotate")

  fun `test svg gradient linear transform translate rotate scale`() =
    compareRendering("test_gradient_linear_transform_translate_rotate_scale")

  fun `test svg gradient linear transform translate scale`() = compareRendering("test_gradient_linear_transform_translate_scale")

  fun `test svg gradient linear transform translate scale shape transform`() =
    compareRendering("test_gradient_linear_transform_translate_scale_shape_transform")

  fun `test svg gradient linear user space on use`() = compareRendering("test_gradient_linear_user_space_on_use")

  fun `test svg gradient linear xy numbers`() = compareRendering("test_gradient_linear_x_y_numbers")

  fun `test svg gradient linear href`() = compareRendering("test_gradient_linear_href")

  fun `test svg gradient transform`() = compareRendering("test_gradient_transform")

  fun `test gradient object transformation`() = compareRendering("test_gradient_object_transformation")

  fun `test svg gradient complex`() = compareRendering("test_gradient_complex")

  fun `test svg gradient complex 2`() = compareRendering("test_gradient_complex_2")

  fun `test svg gradient radial coordinates`() = compareRendering("test_gradient_radial_coordinates")

  fun `test svg gradient radial no coordinates`() = compareRendering("test_gradient_radial_no_coordinates")

  fun `test svg gradient radial no units`() = compareRendering("test_gradient_radial_no_units")

  fun `test svg gradient radial object bounding box`() = compareRendering("test_gradient_radial_object_bounding_box")

  fun `test svg gradient radial one stop`() = compareRendering("test_gradient_radial_one_stop")

  fun `test svg gradient radial overlapping stops`() = compareRendering("test_gradient_radial_overlapping_stops")

  fun `test svg gradient radial r negative`() = compareRendering("test_gradient_radial_r_negative")

  fun `test svg gradient radial r zero`() = compareRendering("test_gradient_radial_r_zero")

  fun `test svg gradient radial spread pad`() = compareRendering("test_gradient_radial_spread_pad")

  fun `test svg gradient radial spread reflect`() = compareRendering("test_gradient_radial_spread_reflect")

  fun `test svg gradient radial spread repeat`() = compareRendering("test_gradient_radial_spread_repeat")

  fun `test svg gradient radial stop opacity`() = compareRendering("test_gradient_radial_stop_opacity")

  fun `test svg gradient radial stop opacity fraction`() = compareRendering("test_gradient_radial_stop_opacity_fraction")

  fun `test svg gradient radial stroke`() = compareRendering("test_gradient_radial_stroke")

  fun `test svg gradient radial three stops`() = compareRendering("test_gradient_radial_three_stops")

  fun `test svg gradient radial user space on use`() = compareRendering("test_gradient_radial_user_space_on_use")

  fun `test svg gradient radial user space 2`() = compareRendering("test_gradient_radial_user_space_2")

  fun `test svg gradient radial transform translate`() = compareRendering("test_gradient_radial_transform_translate")

  fun `test svg gradient radial transform translate userspace`() = compareRendering("test_gradient_radial_transform_translate_userspace")

  fun `test svg gradient radial transform translate scale`() = compareRendering("test_gradient_radial_transform_translate_scale")

  fun `test svg gradient radial transform scale translate`() = compareRendering("test_gradient_radial_transform_scale_translate")

  fun `test svg gradient radial transform matrix`() = compareRendering("test_gradient_radial_transform_matrix")

  fun `test svg gradient radial transform rotate`() = compareRendering("test_gradient_radial_transform_rotate")

  fun `test svg gradient radial transform rotate scale`() = compareRendering("test_gradient_radial_transform_rotate_scale")

  fun `test svg gradient radial transform rotate scale translate`() =
    compareRendering("test_gradient_radial_transform_rotate_scale_translate")

  fun `test svg gradient radial transform rotate translate`() = compareRendering("test_gradient_radial_transform_rotate_translate")

  fun `test svg gradient radial transform rotate translate 2`() = compareRendering("test_svg_gradient_radial_transform_rotate_translate2")

  fun `test svg gradient radial transform rotate translate scale`() =
    compareRendering("test_gradient_radial_transform_rotate_translate_scale")

  fun `test svg gradient radial transform scale`() = compareRendering("test_gradient_radial_transform_scale")

  fun `test svg gradient radial transform scale rotate`() = compareRendering("test_gradient_radial_transform_scale_rotate")

  fun `test svg gradient radial transform scale rotate translate`() =
    compareRendering("test_gradient_radial_transform_scale_rotate_translate")

  fun `test svg gradient radial transform scale translate rotate`() =
    compareRendering("test_gradient_radial_transform_scale_translate_rotate")

  fun `test svg gradient radial transform translate rotate`() = compareRendering("test_gradient_radial_transform_translate_rotate")

  fun `test svg gradient radial transform translate rotate scale`() =
    compareRendering("test_gradient_radial_transform_translate_rotate_scale")

  fun `test svg gradient radial transform translate scale rotate`() =
    compareRendering("test_gradient_radial_transform_translate_scale_rotate")

  fun `test svg gradient radial transform translate group scale translate`() =
    compareRendering("test_gradient_radial_transform_translate_group_scale_translate")

  fun `test svg gradient radial units as numbers`() = compareRendering("test_gradient_radial_units_as_numbers")

  fun `test svg gradient radial coordinates negative percentage`() =
    compareRendering("test_gradient_radial_coordinates_negative_percentage")

  fun `test clip path order`() = compareRendering("ic_clip_path_ordering")

  fun `test svg nested use`() = compareRendering("test_nested_use")

  fun `test svg defs use circular dependency`() = compareRendering("test_defs_use_circular_dependency")

  fun `test svg unsupported element`() = compareRendering("test_unsupported_element")

  fun `test svg image only`() = compareRendering("test_image_only")

  fun `test svg mask unsupported`() = compareRendering("test_mask_unsupported")

  fun `test svg gradient radial no stops`() = compareRendering("test_gradient_radial_no_stops")

  fun `test svg gradient linear unsupported color`() = compareRendering("test_gradient_linear_unsupported_color")

  fun `test svg stroke width nonuniform transform`() = compareRendering("ic_strokewidth_nonuniform_transform")

  fun `test svg semi transparent mask not valid`() = compareRendering("ic_semitransparent_mask")

  fun `test svg contains error`() = compareRendering("ic_contains_ignorable_error")

  fun `test parse error`() = compareRendering("test_parse_error")

  fun `test locale with decimal comma`() {
    val defaultLocale = Locale.getDefault()
    Locale.setDefault(Locale.FRANCE)

    try {
      compareRendering("test_locale_with_decimal_comma")
    }
    finally {
      Locale.setDefault(defaultLocale)
    }
  }

  fun `test locale with dash as minus`() {
    val defaultLocale = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag("sv-SE"))

    try {
      compareRendering("test_locale_with_dash_as_minus")
    }
    finally {
      Locale.setDefault(defaultLocale)
    }
  }

  private fun compareRendering(testCase: String) {
    val svg = loadSvgResource(testCase)
    val kotlinErrors = StringBuilder()
    val androidErrors = StringBuilder()

    val kotlinXml = kotlinRenderer.convertSvgToVectorDrawable(svg, kotlinErrors)
    val androidXml = androidRenderer.convertSvgToVectorDrawable(svg, androidErrors)

    val kotlinEmpty = kotlinXml.isNullOrBlank()
    val androidEmpty = androidXml.isNullOrBlank()

    if (kotlinEmpty && androidEmpty) return

    if (androidErrors.isNotEmpty()) assertEquals(androidErrors.toString(), kotlinErrors.toString())

    if (kotlinEmpty || androidEmpty) {
      fail("Conversion mismatch for $testCase: Android=${if (androidEmpty) "null" else "success"}, Kotlin=${if (kotlinEmpty) "null" else "success"}")
      return
    }

    assertEquals(
      "XML output mismatch for $testCase",
      normalizeAttributeOrder(androidXml),
      normalizeAttributeOrder(kotlinXml),
    )
  }

  private fun loadSvgResource(testCase: String): Path {
    val fileName = "$testCase.svg"
    val path = "plugins/compose/intellij.compose.ide.plugin.resources/testData/vectordrawable/$fileName"
    val file = File(PlatformTestUtil.getCommunityPath(), path)
    if (file.exists()) return file.toPath()

    val stream = javaClass.classLoader.getResourceAsStream("testData/vectordrawable/$fileName")
                 ?: error("Test resource not found: $testCase")
    val tempFile = Files.createTempFile(testCase, ".svg")
    Files.writeString(tempFile, stream.bufferedReader().use { it.readText() })
    return tempFile
  }

  private fun normalizeAttributeOrder(xml: String): String {
    return xml.lines().joinToString("\n") { line ->
      line
    }.let { content ->
      val factory = DocumentBuilderFactory.newInstance()
      val builder = factory.newDocumentBuilder()
      val doc = builder.parse(InputSource(StringReader(content)))

      val transformer = TransformerFactory.newInstance().newTransformer()
      transformer.setOutputProperty(OutputKeys.INDENT, "yes")
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")

      StringWriter().also {
        transformer.transform(DOMSource(doc), StreamResult(it))
      }.toString()
    }
  }
}