// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.highlighter.JavaHighlightingColors
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.editor.colors.*
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.editor.colors.impl.EmptyColorScheme
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.ColorUtil
import org.jetbrains.idea.eclipse.importer.colors.EclipseColorSchemeImporter
import org.junit.Assert
import java.awt.Color
import java.awt.Font
import kotlin.io.path.div

class EclipseColorSchemeImportTest : LightPlatformTestCase() {
  fun testImport() {
    val input = VfsUtil.findFile(eclipseTestDataRoot / "import" / "colorSchemes" / "eclipseColorTheme.xml", false)!!
    val importer = EclipseColorSchemeImporter()
    val imported = importer.importScheme(project, input, EditorColorsManager.getInstance().globalScheme) {
      val scheme = EditorColorsSchemeImpl(EmptyColorScheme.INSTANCE)
      scheme.name = "EclipseColorSchemeImportTestScheme"
      scheme.setDefaultMetaInfo(EmptyColorScheme.INSTANCE)
      scheme
    }!!

    imported.apply {
      val lineCommentColor = "#9B98DC".toColor()
      assertAttrs(DefaultLanguageHighlighterColors.LINE_COMMENT,
                  TextAttributes(lineCommentColor, null, lineCommentColor, EffectType.STRIKEOUT, Font.PLAIN))
      val blockCommentColor = "#B2B586".toColor()
      assertAttrs(DefaultLanguageHighlighterColors.BLOCK_COMMENT,
                  TextAttributes(blockCommentColor, null, blockCommentColor, EffectType.LINE_UNDERSCORE, Font.BOLD or Font.ITALIC))
      assertAttrs(DefaultLanguageHighlighterColors.DOC_COMMENT, TextAttributes("#A8595D".toColor(), null, null, null, Font.ITALIC))
      assertAttrs(DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP, TextAttributes("#412351".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(DefaultLanguageHighlighterColors.DOC_COMMENT_TAG, TextAttributes("#F2340F".toColor(), null, null, null, Font.PLAIN))
      val classColor = "#8DEF29".toColor()
      assertAttrs(DefaultLanguageHighlighterColors.CLASS_NAME, TextAttributes(classColor, null, classColor, EffectType.LINE_UNDERSCORE, Font.PLAIN))
      assertAttrs(DefaultLanguageHighlighterColors.INTERFACE_NAME, TextAttributes("#C4D416".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(DefaultLanguageHighlighterColors.FUNCTION_CALL, TextAttributes("#91B67E".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION, TextAttributes("#C77C30".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(DefaultLanguageHighlighterColors.NUMBER, TextAttributes("#F0081F".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(DefaultLanguageHighlighterColors.METADATA, TextAttributes("#4F2344".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(DefaultLanguageHighlighterColors.STATIC_METHOD, TextAttributes("#AD0E4F".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(DefaultLanguageHighlighterColors.STATIC_FIELD, TextAttributes("#E02E46".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(DefaultLanguageHighlighterColors.PARAMETER, TextAttributes("#E7CCDA".toColor(), null, null, null, Font.BOLD))
      assertAttrs(DefaultLanguageHighlighterColors.CONSTANT, TextAttributes("#DEE334".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(CodeInsightColors.TODO_DEFAULT_ATTRIBUTES, TextAttributes("#FEE51D".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(JavaHighlightingColors.TYPE_PARAMETER_NAME_ATTRIBUTES, TextAttributes("#2944EB".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(JavaHighlightingColors.ENUM_NAME_ATTRIBUTES, TextAttributes("#FC1D19".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(JavaHighlightingColors.INHERITED_METHOD_ATTRIBUTES, TextAttributes("#5B6C3E".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(JavaHighlightingColors.ABSTRACT_METHOD_ATTRIBUTES, TextAttributes("#ACC094".toColor(), null, null, null, Font.PLAIN))
      assertAttrs(JavaHighlightingColors.STATIC_FINAL_FIELD_ATTRIBUTES, TextAttributes("#EF9F0C".toColor(), null, null, null, Font.PLAIN))
      val deprecatedColor = "#0E5B6C".toColor()
      assertAttrs(CodeInsightColors.DEPRECATED_ATTRIBUTES,
                  TextAttributes(deprecatedColor, null, deprecatedColor, EffectType.STRIKEOUT, Font.PLAIN))

      val foregroundColor = "#5E6BCF".toColor()
      val backgroundColor = "#020202".toColor()
      assertAttrs(HighlighterColors.TEXT, TextAttributes(foregroundColor, backgroundColor, null, null, Font.PLAIN))
      assertColor(EditorColors.GUTTER_BACKGROUND, backgroundColor)
      assertColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY, backgroundColor)
      assertAttrs(DefaultLanguageHighlighterColors.IDENTIFIER, TextAttributes(foregroundColor, null, null, null, Font.PLAIN))
      assertColor(EditorColors.CARET_ROW_COLOR, "#7BD1E5".toColor())
      assertColor(EditorColors.SELECTION_BACKGROUND_COLOR, "#40A1F8".toColor())
      assertColor(EditorColors.SELECTION_FOREGROUND_COLOR, "#FA55CD".toColor())
      assertAttrs(JavaHighlightingColors.STATIC_FINAL_FIELD_ATTRIBUTES, TextAttributes("#EF9F0C".toColor(), null, null, null, Font.PLAIN))
      val bracketAttrs = TextAttributes("#A66B89".toColor(), null, null, null, Font.PLAIN)
      assertAttrs(DefaultLanguageHighlighterColors.BRACES, bracketAttrs)
      assertAttrs(DefaultLanguageHighlighterColors.BRACKETS, bracketAttrs)
      assertAttrs(DefaultLanguageHighlighterColors.PARENTHESES, bracketAttrs)
      val operatorAttrs = TextAttributes("#D463E8".toColor(), null, null, null, Font.PLAIN)
      assertAttrs(DefaultLanguageHighlighterColors.OPERATION_SIGN, operatorAttrs)
      assertAttrs(DefaultLanguageHighlighterColors.COMMA, operatorAttrs)
      assertAttrs(DefaultLanguageHighlighterColors.SEMICOLON, operatorAttrs)
      assertAttrs(DefaultLanguageHighlighterColors.DOT, operatorAttrs)
      val lineNumbersColor = "#744838".toColor()
      assertColor(EditorColors.LINE_NUMBERS_COLOR, lineNumbersColor)
      assertColor(EditorColors.TEARLINE_COLOR, lineNumbersColor)
      assertColor(EditorColors.RIGHT_MARGIN_COLOR, lineNumbersColor)
      assertColor(EditorColors.CARET_COLOR, lineNumbersColor)
      val localVarAttrs = TextAttributes("#00719E".toColor(), null, null, null, Font.PLAIN)
      assertAttrs(DefaultLanguageHighlighterColors.LOCAL_VARIABLE, localVarAttrs)
      assertAttrs(DefaultLanguageHighlighterColors.MARKUP_TAG, localVarAttrs)
      val localVarDeclAttrs = TextAttributes("#FE6DB1".toColor(), null, null, null, Font.PLAIN)
      assertAttrs(XmlHighlighterColors.HTML_TAG_NAME, localVarDeclAttrs)
      assertAttrs(XmlHighlighterColors.XML_TAG_NAME, localVarDeclAttrs)
      val fieldAttrs = TextAttributes("#3347ED".toColor(), null, null, null, Font.PLAIN)
      assertAttrs(DefaultLanguageHighlighterColors.INSTANCE_FIELD, fieldAttrs)
      assertAttrs(XmlHighlighterColors.XML_ATTRIBUTE_NAME, fieldAttrs)
      assertAttrs(XmlHighlighterColors.HTML_ATTRIBUTE_NAME, fieldAttrs)
      assertAttrs(EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES, TextAttributes(null, "#1B7D32".toColor(), null, null, Font.PLAIN))
      assertAttrs(EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES, TextAttributes(null, "#A2790C".toColor(), null, null, Font.PLAIN))
      assertAttrs(EditorColors.SEARCH_RESULT_ATTRIBUTES, TextAttributes(null, "#959076".toColor(), null, null, Font.PLAIN))
      assertAttrs(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES, TextAttributes(null, "#F81C06".toColor(), null, null, Font.PLAIN))
      val stringColor = "#A86B3A".toColor()
      assertAttrs(DefaultLanguageHighlighterColors.STRING, TextAttributes(stringColor, null, null, null, Font.PLAIN))
      assertAttrs(DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE, TextAttributes(stringColor, null, null, null, Font.BOLD))
      val keywordAttrs = TextAttributes("#0B42FA".toColor(), null, null, null, Font.PLAIN)
      assertAttrs(DefaultLanguageHighlighterColors.KEYWORD, keywordAttrs)
      assertAttrs(DefaultLanguageHighlighterColors.MARKUP_ENTITY, keywordAttrs)

      val defaultScheme = DefaultColorSchemesManager.getInstance().getScheme("Darcula")!!
      assertAttrsSameAsIn(HighlighterColors.BAD_CHARACTER, defaultScheme)
      assertColorSameAsIn(EditorColors.ANNOTATIONS_COLOR, defaultScheme)
      assertAttrsSameAsIn(TextAttributesKey.createTextAttributesKey("BREAKPOINT_ATTRIBUTES"), defaultScheme)
      assertColorSameAsIn(ColorKey.createColorKey("VCS_ANNOTATIONS_COLOR_1"), defaultScheme)

      val lightForeground = ColorUtil.mix(foregroundColor, backgroundColor, 0.5)
      val errorColor = ColorUtil.mix(backgroundColor, Color.RED, 0.5)
      assertColor(EditorColors.WHITESPACES_COLOR, lightForeground)
      assertColor(EditorColors.INDENT_GUIDE_COLOR, lightForeground)
      assertColor(EditorColors.SOFT_WRAP_SIGN_COLOR, lightForeground)
      assertAttrs(DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE, TextAttributes(stringColor, null, null, null, Font.BOLD))
      assertAttrs(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES, TextAttributes(null, null, lightForeground, EffectType.BOXED, Font.PLAIN))
      assertAttrs(CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES, TextAttributes(null, null, errorColor, EffectType.BOXED, Font.PLAIN))
      assertAttrs(CodeInsightColors.MARKED_FOR_REMOVAL_ATTRIBUTES,
                  TextAttributes(deprecatedColor, null, errorColor, EffectType.STRIKEOUT, Font.PLAIN))
      assertAttrs(JavaHighlightingColors.PUBLIC_REFERENCE_ATTRIBUTES, TextAttributes())
      assertAttrs(JavaHighlightingColors.PRIVATE_REFERENCE_ATTRIBUTES, TextAttributes())
      assertAttrs(JavaHighlightingColors.PACKAGE_PRIVATE_REFERENCE_ATTRIBUTES, TextAttributes())
      assertAttrs(JavaHighlightingColors.PROTECTED_REFERENCE_ATTRIBUTES, TextAttributes())
    }
  }

  private fun EditorColorsScheme.assertAttrsSameAsIn(key: TextAttributesKey, scheme: EditorColorsScheme) {
    val attrs = scheme.getAttributes(key) ?: fail("Attributes \"${key.externalName}\" not found in color scheme \"${scheme.name}\".")
    assertAttrs(key, attrs)
  }

  private fun EditorColorsScheme.assertColorSameAsIn(key: ColorKey, scheme: EditorColorsScheme) {
    val color = scheme.getColor(key) ?: fail("Color \"${key.externalName}\" not found in color scheme \"${scheme.name}\".")
    assertColor(key, color)
  }

  private fun EditorColorsScheme.assertAttrs(key: TextAttributesKey, expected: TextAttributes) {
    val actual = this.getAttributes(key) ?: fail("Attributes \"${key.externalName}\" not found.")
    // effect type does not need to match if effect color is null
    val actualFixed: TextAttributes
    if (actual.effectColor == null && expected.effectColor == null) {
      actualFixed = actual.clone()
      actualFixed.effectType = expected.effectType
    }
    else {
      actualFixed = actual
    }
    assertEquals("Attributes \"${key.externalName}\" do not match.", expected, actualFixed)
  }

  private fun EditorColorsScheme.assertColor(key: ColorKey, expected: Color) {
    val actual = this.getColor(key) ?: fail("Color \"${key.externalName}\" not found.")
    assertEquals("Color \"${key.externalName}\" does not match.", expected, actual)
  }

  companion object {
    private fun String.toColor(): Color = Color.decode(this)!!

    private fun fail(msg: String): Nothing {
      Assert.fail(msg)
      // unreachable
      throw AssertionError(msg)
    }
  }
}