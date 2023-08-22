// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.importer.colors;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class EclipseThemeOptionHandler implements EclipseThemeReader.OptionHandler, EclipseColorThemeElements {
  private final EditorColorsScheme myColorsScheme;

  private static final Map<String, TextAttributesKey> ECLIPSE_TO_IDEA_ATTR_MAP = new HashMap<>();
    
  static {
    ECLIPSE_TO_IDEA_ATTR_MAP.put(SINGLE_LINE_COMMENT_TAG, DefaultLanguageHighlighterColors.LINE_COMMENT);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(MULTI_LINE_COMMENT_TAG, DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(JAVADOC_TAG, DefaultLanguageHighlighterColors.DOC_COMMENT);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(JAVADOC_TAG_TAG, DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(JAVADOC_KEYWORD_TAG, DefaultLanguageHighlighterColors.DOC_COMMENT_TAG);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(CLASS_TAG, DefaultLanguageHighlighterColors.CLASS_NAME);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(INTERFACE_TAG, DefaultLanguageHighlighterColors.INTERFACE_NAME);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(METHOD_TAG, DefaultLanguageHighlighterColors.FUNCTION_CALL);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(METHOD_DECLARATION, DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(NUMBER_TAG, DefaultLanguageHighlighterColors.NUMBER);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(ANNOTATION_TAG, DefaultLanguageHighlighterColors.METADATA);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(STATIC_METHOD_TAG, DefaultLanguageHighlighterColors.STATIC_METHOD);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(STATIC_FIELD_TAG, DefaultLanguageHighlighterColors.STATIC_FIELD);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(PARAMETER_VAR_TAG, DefaultLanguageHighlighterColors.PARAMETER);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(CONST_TAG, DefaultLanguageHighlighterColors.CONSTANT);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(COMMENT_TASK_TAG, CodeInsightColors.TODO_DEFAULT_ATTRIBUTES);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(TYPE_ARG_TAG, JavaHighlightingColors.TYPE_PARAMETER_NAME_ATTRIBUTES);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(ENUM_TAG, JavaHighlightingColors.ENUM_NAME_ATTRIBUTES);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(INHERITED_METHOD_TAG, JavaHighlightingColors.INHERITED_METHOD_ATTRIBUTES);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(ABSTRACT_METHOD_TAG, JavaHighlightingColors.ABSTRACT_METHOD_ATTRIBUTES);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(STATIC_FINAL_FIELD_TAG, JavaHighlightingColors.STATIC_FINAL_FIELD_ATTRIBUTES);
    ECLIPSE_TO_IDEA_ATTR_MAP.put(DEPRECATED_MEMBER_TAG, CodeInsightColors.DEPRECATED_ATTRIBUTES);
  }

  public EclipseThemeOptionHandler(EditorColorsScheme colorsScheme) {
    myColorsScheme = colorsScheme;
  }

  @Override
  public void handleColorOption(@NotNull String name, @NotNull TextAttributes attributes) {
    switch (name) {
      case BACKGROUND_TAG -> {
        updateAttributes(HighlighterColors.TEXT, attributes);
        myColorsScheme.setColor(EditorColors.GUTTER_BACKGROUND, attributes.getBackgroundColor());
        myColorsScheme.setColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY, attributes.getBackgroundColor());
      }
      case FOREGROUND_TAG -> {
        updateAttributes(HighlighterColors.TEXT, attributes);
        updateAttributes(DefaultLanguageHighlighterColors.IDENTIFIER, attributes);
      }
      case CURR_LINE_TAG -> myColorsScheme.setColor(EditorColors.CARET_ROW_COLOR, attributes.getForegroundColor());
      case SELECTION_BACKGROUND_TAG -> myColorsScheme.setColor(EditorColors.SELECTION_BACKGROUND_COLOR, attributes.getBackgroundColor());
      case SELECTION_FOREGROUND_TAG -> myColorsScheme.setColor(EditorColors.SELECTION_FOREGROUND_COLOR, attributes.getForegroundColor());
      case BRACKET -> {
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.BRACES, attributes);
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.BRACKETS, attributes);
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.PARENTHESES, attributes);
      }
      case OPERATOR_TAG -> {
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.OPERATION_SIGN, attributes);
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.COMMA, attributes);
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.SEMICOLON, attributes);
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.DOT, attributes);
      }
      case LINE_NUMBER_TAG -> {
        Color lineNumberColor = attributes.getForegroundColor();
        myColorsScheme.setColor(EditorColors.LINE_NUMBERS_COLOR, lineNumberColor);
        myColorsScheme.setColor(EditorColors.TEARLINE_COLOR, lineNumberColor);
        myColorsScheme.setColor(EditorColors.RIGHT_MARGIN_COLOR, lineNumberColor);
        myColorsScheme.setColor(EditorColors.CARET_COLOR, lineNumberColor);
      }
      case LOCAL_VARIABLE_TAG -> {
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.LOCAL_VARIABLE, attributes);
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.MARKUP_TAG, attributes);
      }
      case LOCAL_VARIABLE_DECL_TAG -> {
        myColorsScheme.setAttributes(XmlHighlighterColors.HTML_TAG_NAME, attributes);
        myColorsScheme.setAttributes(XmlHighlighterColors.XML_TAG_NAME, attributes);
      }
      case FIELD_TAG -> {
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD, attributes);
        myColorsScheme.setAttributes(XmlHighlighterColors.XML_ATTRIBUTE_NAME, attributes);
        myColorsScheme.setAttributes(XmlHighlighterColors.HTML_ATTRIBUTE_NAME, attributes);
      }
      case OCCURENCE_TAG ->
        myColorsScheme.setAttributes(EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES, toBackgroundAttributes(attributes));
      case WRITE_OCCURENCE_TAG ->
        myColorsScheme.setAttributes(EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES, toBackgroundAttributes(attributes));
      case SEARCH_RESULT_TAG -> myColorsScheme.setAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES, toBackgroundAttributes(attributes));
      case FILTER_SEARCH_RESULT_TAG ->
        myColorsScheme.setAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES, toBackgroundAttributes(attributes));
      case STRING_TAG -> {
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.STRING, attributes);
        TextAttributes validEscapeAttrs = attributes.clone();
        validEscapeAttrs.setFontType(Font.BOLD);
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE, validEscapeAttrs);
      }
      case KEYWORD_TAG -> {
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.KEYWORD, attributes);
        myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.MARKUP_ENTITY, attributes);
      }
      default -> {
        if (ECLIPSE_TO_IDEA_ATTR_MAP.containsKey(name)) {
          myColorsScheme.setAttributes(ECLIPSE_TO_IDEA_ATTR_MAP.get(name), attributes);
        }
      }
    }
  }

  private void updateAttributes(@NotNull TextAttributesKey key, @NotNull TextAttributes attributes) {
    if (myColorsScheme instanceof AbstractColorsScheme) {
      TextAttributes alreadyDefined = ((AbstractColorsScheme)myColorsScheme).getDirectlyDefinedAttributes(key);
      if (alreadyDefined != null) {
        alreadyDefined = alreadyDefined.clone();
        if (attributes.getForegroundColor() != null) alreadyDefined.setForegroundColor(attributes.getForegroundColor());
        if (attributes.getBackgroundColor() != null) alreadyDefined.setBackgroundColor(attributes.getBackgroundColor());
        myColorsScheme.setAttributes(key, alreadyDefined);
        return;
      }
    }
    myColorsScheme.setAttributes(key, attributes);
  }
  
  private static TextAttributes toBackgroundAttributes(@NotNull TextAttributes attributes) {
    TextAttributes backgroundAttrs = attributes.clone();
    backgroundAttrs.setBackgroundColor(attributes.getForegroundColor());
    backgroundAttrs.setForegroundColor(null);
    return backgroundAttrs;
  }
}
