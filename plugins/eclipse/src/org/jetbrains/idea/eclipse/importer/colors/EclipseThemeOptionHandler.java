/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

@SuppressWarnings("UseJBColor")
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
    if (BACKGROUND_TAG.equals(name)) {
      updateAttributes(HighlighterColors.TEXT, attributes);
      myColorsScheme.setColor(EditorColors.GUTTER_BACKGROUND, attributes.getBackgroundColor());
      myColorsScheme.setColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY, attributes.getBackgroundColor());
    }
    else if (FOREGROUND_TAG.equals(name)) {
      updateAttributes(HighlighterColors.TEXT, attributes);
      updateAttributes(DefaultLanguageHighlighterColors.IDENTIFIER, attributes);
    }
    else if (CURR_LINE_TAG.equals(name)) {
      myColorsScheme.setColor(EditorColors.CARET_ROW_COLOR, attributes.getForegroundColor());
    }
    else if (SELECTION_BACKGROUND_TAG.equals(name)) {
      myColorsScheme.setColor(EditorColors.SELECTION_BACKGROUND_COLOR, attributes.getBackgroundColor());
    }
    else if (SELECTION_FOREGROUND_TAG.equals(name)) {
      myColorsScheme.setColor(EditorColors.SELECTION_FOREGROUND_COLOR, attributes.getForegroundColor());
    }
    else if (BRACKET.equals(name)) {
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.BRACES, attributes);
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.BRACKETS, attributes);
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.PARENTHESES, attributes);
    }
    else if (OPERATOR_TAG.equals(name)) {
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.OPERATION_SIGN, attributes);
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.COMMA, attributes);
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.SEMICOLON, attributes);
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.DOT, attributes);
    }
    else if (LINE_NUMBER_TAG.equals(name)) {
      Color lineNumberColor = attributes.getForegroundColor();
      myColorsScheme.setColor(EditorColors.LINE_NUMBERS_COLOR, lineNumberColor);
      myColorsScheme.setColor(EditorColors.TEARLINE_COLOR, lineNumberColor);
      myColorsScheme.setColor(EditorColors.RIGHT_MARGIN_COLOR, lineNumberColor);
      myColorsScheme.setColor(EditorColors.CARET_COLOR, lineNumberColor);
    }
    else if(LOCAL_VARIABLE_TAG.equals(name)) {
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.LOCAL_VARIABLE, attributes);
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.MARKUP_TAG, attributes);
    }
    else if(LOCAL_VARIABLE_DECL_TAG.equals(name)) {
      myColorsScheme.setAttributes(XmlHighlighterColors.HTML_TAG_NAME, attributes);
      myColorsScheme.setAttributes(XmlHighlighterColors.XML_TAG_NAME, attributes);
    }
    else if(FIELD_TAG.equals(name)) {
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD, attributes);
      myColorsScheme.setAttributes(XmlHighlighterColors.XML_ATTRIBUTE_NAME, attributes);
      myColorsScheme.setAttributes(XmlHighlighterColors.HTML_ATTRIBUTE_NAME, attributes);
    }
    else if(OCCURENCE_TAG.equals(name)) {
      myColorsScheme.setAttributes(EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES, toBackgroundAttributes(attributes));
    }
    else if (WRITE_OCCURENCE_TAG.equals(name)) {
      myColorsScheme.setAttributes(EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES, toBackgroundAttributes(attributes));
    }
    else if(SEARCH_RESULT_TAG.equals(name)) {
      myColorsScheme.setAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES, toBackgroundAttributes(attributes));
    }
    else if(FILTER_SEARCH_RESULT_TAG.equals(name)) {
      myColorsScheme.setAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES, toBackgroundAttributes(attributes));
    }
    else if (STRING_TAG.equals(name)) {
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.STRING, attributes);
      TextAttributes validEscapeAttrs = attributes.clone();
      validEscapeAttrs.setFontType(Font.BOLD);
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE, validEscapeAttrs);
    }
    else if (KEYWORD_TAG.equals(name)) {
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.KEYWORD, attributes);
      myColorsScheme.setAttributes(DefaultLanguageHighlighterColors.MARKUP_ENTITY, attributes);
    }
    else {
      if (ECLIPSE_TO_IDEA_ATTR_MAP.containsKey(name)) {
        myColorsScheme.setAttributes(ECLIPSE_TO_IDEA_ATTR_MAP.get(name), attributes);
      }
    }
  }

  private void updateAttributes(@NotNull TextAttributesKey key, @NotNull TextAttributes attributes) {
    if (myColorsScheme instanceof AbstractColorsScheme) {
      TextAttributes alreadyDefined = ((AbstractColorsScheme)myColorsScheme).getDirectlyDefinedAttributes(key);
      if (alreadyDefined != null) {
        if (attributes.getForegroundColor() != null) alreadyDefined.setForegroundColor(attributes.getForegroundColor());
        if (attributes.getBackgroundColor() != null) alreadyDefined.setBackgroundColor(attributes.getBackgroundColor());
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
