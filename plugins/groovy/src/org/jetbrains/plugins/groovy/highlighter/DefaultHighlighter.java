/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.PlatformColors;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * @author ilyas
 */
public class DefaultHighlighter {

  @NonNls
  static final String LINE_COMMENT_ID = "Line comment";
  @NonNls
  static final String BLOCK_COMMENT_ID = "Block comment";
  @NonNls
  static final String DOC_COMMENT_ID = "Groovydoc comment";
  @NonNls
  static final String DOC_COMMENT_TAG_ID = "Groovydoc tag";
  @NonNls
  static final String KEYWORD_ID = "Keyword";
  @NonNls
  static final String NUMBER_ID = "Number";
  @NonNls
  static final String GSTRING_ID = "GString";
  @NonNls
  static final String STRING_ID = "String";
  @NonNls
  static final String BRACES_ID = "Braces";
  @NonNls
  static final String BRACKETS_ID = "Brackets";
  @NonNls
  static final String PARENTHESES_ID = "Parentheses";
  @NonNls
  static final String OPERATION_SIGN_ID = "Operation sign";
  @NonNls
  static final String BAD_CHARACTER_ID = "Bad character";
  @NonNls
  static final String ANNOTATION_ID = "Annotation";
  @NonNls
  static final String INSTANCE_FIELD_ID = "Instance field";
  @NonNls
  static final String STATIC_FIELD_ID = "Static field";
  @NonNls
  static final String METHOD_CALL_ID = "Method call";
  @NonNls
  static final String STATIC_METHOD_ACCESS_ID = "Static method access";
  @NonNls
  static final String CLASS_REFERENCE_ID = "Class";
  @NonNls
  static final String TYPE_PARAMETER_ID = "Type parameter";
  @NonNls
  static final String INSTANCE_PROPERTY_REFERENCE_ID = "Instance property reference ID";
  @NonNls
  static final String STATIC_PROPERTY_REFERENCE_ID = "Static property reference ID";
  @NonNls
  static final String MAP_KEY_ID = "Map key";
  @NonNls
  static final String UNRESOLVED_ACCESS_ID = "Unresolved reference access";
  @NonNls
  static final String LITERAL_CONVERSION_ID = "List/map to object conversion";
  @NonNls
  static final String VALID_STRING_ESCAPE_ID = "Valid string escape";
  @NonNls
  static final String INVALID_STRING_ESCAPE_ID = "Invalid string escape";
  @NonNls
  static final String LABEL_ID = "Label";

  public static final TextAttributesKey LINE_COMMENT =
    TextAttributesKey.createTextAttributesKey(LINE_COMMENT_ID, DefaultLanguageHighlighterColors.LINE_COMMENT);

  public static final TextAttributesKey ANNOTATION =
    TextAttributesKey.createTextAttributesKey(ANNOTATION_ID, HighlightInfoType.ANNOTATION_NAME.getAttributesKey());
  
  public static final TextAttributesKey LOCAL_VARIABLE =
    TextAttributesKey.createTextAttributesKey("Groovy var", HighlightInfoType.LOCAL_VARIABLE.getAttributesKey());
  
  public static final TextAttributesKey REASSIGNED_LOCAL_VARIABLE =
    TextAttributesKey.createTextAttributesKey("Groovy reassigned var", HighlightInfoType.REASSIGNED_LOCAL_VARIABLE.getAttributesKey());

  public static final TextAttributesKey PARAMETER =
    TextAttributesKey.createTextAttributesKey("Groovy parameter", HighlightInfoType.PARAMETER.getAttributesKey());
  
  public static final TextAttributesKey REASSIGNED_PARAMETER = 
    TextAttributesKey.createTextAttributesKey("Groovy reassigned parameter", HighlightInfoType.REASSIGNED_PARAMETER.getAttributesKey());

  public static final TextAttributesKey METHOD_DECLARATION =
    TextAttributesKey.createTextAttributesKey("Groovy method declaration", HighlightInfoType.METHOD_DECLARATION.getAttributesKey());

  public static final TextAttributesKey CONSTRUCTOR_DECLARATION = TextAttributesKey
    .createTextAttributesKey("Groovy constructor declaration", HighlightInfoType.CONSTRUCTOR_DECLARATION.getAttributesKey());

  public static final TextAttributesKey INSTANCE_FIELD = 
    TextAttributesKey.createTextAttributesKey(INSTANCE_FIELD_ID, HighlightInfoType.INSTANCE_FIELD.getAttributesKey());
  
  public static final TextAttributesKey METHOD_CALL = 
    TextAttributesKey.createTextAttributesKey(METHOD_CALL_ID, HighlightInfoType.METHOD_CALL.getAttributesKey());

  public static final TextAttributesKey CONSTRUCTOR_CALL = TextAttributesKey
    .createTextAttributesKey("Groovy constructor call", HighlightInfoType.CONSTRUCTOR_CALL.getAttributesKey());

  public static final TextAttributesKey STATIC_FIELD = 
    TextAttributesKey.createTextAttributesKey(STATIC_FIELD_ID, HighlightInfoType.STATIC_FINAL_FIELD.getAttributesKey());

  public static final TextAttributesKey STATIC_METHOD_ACCESS = 
    TextAttributesKey.createTextAttributesKey(STATIC_METHOD_ACCESS_ID, HighlightInfoType.STATIC_METHOD.getAttributesKey());

  public static final TextAttributesKey BLOCK_COMMENT = 
    TextAttributesKey.createTextAttributesKey(BLOCK_COMMENT_ID, JavaHighlightingColors.JAVA_BLOCK_COMMENT);

  public static final TextAttributesKey DOC_COMMENT_CONTENT = 
    TextAttributesKey.createTextAttributesKey(DOC_COMMENT_ID, JavaHighlightingColors.DOC_COMMENT);

  public static final TextAttributesKey DOC_COMMENT_TAG = 
    TextAttributesKey.createTextAttributesKey(DOC_COMMENT_TAG_ID, JavaHighlightingColors.DOC_COMMENT_TAG);

  public static final TextAttributesKey CLASS_REFERENCE = 
    TextAttributesKey.createTextAttributesKey(CLASS_REFERENCE_ID, HighlighterColors.TEXT);

  public static final TextAttributesKey TYPE_PARAMETER = 
    TextAttributesKey.createTextAttributesKey(TYPE_PARAMETER_ID, CodeInsightColors.TYPE_PARAMETER_NAME_ATTRIBUTES);

  public static final TextAttributes INSTANCE_PROPERTY_REFERENCE_ATTRIBUTES = INSTANCE_FIELD.getDefaultAttributes().clone();
  public static final TextAttributes STATIC_PROPERTY_REFERENCE_ATTRIBUTES = STATIC_FIELD.getDefaultAttributes().clone();
  static {
    INSTANCE_PROPERTY_REFERENCE_ATTRIBUTES.setFontType(Font.PLAIN);
    STATIC_PROPERTY_REFERENCE_ATTRIBUTES.setFontType(Font.ITALIC);
  }

  public static final TextAttributesKey INSTANCE_PROPERTY_REFERENCE =
    TextAttributesKey.createTextAttributesKey(INSTANCE_PROPERTY_REFERENCE_ID, INSTANCE_PROPERTY_REFERENCE_ATTRIBUTES);

  public static final TextAttributesKey STATIC_PROPERTY_REFERENCE =
    TextAttributesKey.createTextAttributesKey(STATIC_PROPERTY_REFERENCE_ID, STATIC_PROPERTY_REFERENCE_ATTRIBUTES);

  public static final TextAttributes KEYWORD_ATTRIBUTES = JavaHighlightingColors.KEYWORD.getDefaultAttributes().clone();

  public static final TextAttributesKey KEYWORD = TextAttributesKey.createTextAttributesKey("GROOVY_" + KEYWORD_ID.toUpperCase(), KEYWORD_ATTRIBUTES);
  static {
    KEYWORD_ATTRIBUTES.setForegroundColor(new JBColor(new Color(0, 0, 67), new Color(0, 0, 67)));
    KEYWORD_ATTRIBUTES.setFontType(Font.BOLD);
  }

  public static final TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey(NUMBER_ID, JavaHighlightingColors.NUMBER);

  public static final TextAttributesKey GSTRING = TextAttributesKey.createTextAttributesKey(GSTRING_ID, JavaHighlightingColors.STRING);

  public static final TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey(STRING_ID, JavaHighlightingColors.STRING);

  public static final TextAttributesKey BRACES = TextAttributesKey.createTextAttributesKey(BRACES_ID, JavaHighlightingColors.BRACES);

  public static final TextAttributesKey BRACKETS = TextAttributesKey.createTextAttributesKey(BRACKETS_ID, JavaHighlightingColors.BRACKETS);

  public static final TextAttributesKey PARENTHESES = TextAttributesKey.createTextAttributesKey(PARENTHESES_ID, JavaHighlightingColors.PARENTHESES);

  public static final TextAttributesKey OPERATION_SIGN = TextAttributesKey.createTextAttributesKey(OPERATION_SIGN_ID, JavaHighlightingColors.OPERATION_SIGN);

  public static final TextAttributesKey BAD_CHARACTER = TextAttributesKey.createTextAttributesKey(BAD_CHARACTER_ID, CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES);

  public static final TextAttributes UNRESOLVED_ACCESS_ATTRIBUTES = HighlighterColors.TEXT.getDefaultAttributes().clone();
  static {
    UNRESOLVED_ACCESS_ATTRIBUTES.setForegroundColor(JBColor.BLACK);
    UNRESOLVED_ACCESS_ATTRIBUTES.setEffectColor(JBColor.GRAY);
    UNRESOLVED_ACCESS_ATTRIBUTES.setEffectType(EffectType.LINE_UNDERSCORE);
  }

  public static final TextAttributesKey UNRESOLVED_ACCESS =
    TextAttributesKey.createTextAttributesKey(UNRESOLVED_ACCESS_ID, UNRESOLVED_ACCESS_ATTRIBUTES);


  public static final TextAttributes LITERAL_CONVERSION_ATTRIBUTES = HighlighterColors.TEXT.getDefaultAttributes().clone();
  static{
    LITERAL_CONVERSION_ATTRIBUTES.setForegroundColor(PlatformColors.BLUE);
    LITERAL_CONVERSION_ATTRIBUTES.setFontType(Font.BOLD);
  }

  public static final TextAttributesKey LITERAL_CONVERSION =
    TextAttributesKey.createTextAttributesKey(LITERAL_CONVERSION_ID, LITERAL_CONVERSION_ATTRIBUTES);


  public static final TextAttributes MAP_KEY_ATTRIBUTES = HighlighterColors.TEXT.getDefaultAttributes().clone();

  public static final Color MAP_KEY_COLOR = new JBColor(new Color(0, 128, 0), new Color(0, 128, 0));
  static {
    MAP_KEY_ATTRIBUTES.setForegroundColor(MAP_KEY_COLOR);
  }
  public static final TextAttributesKey MAP_KEY = TextAttributesKey.createTextAttributesKey(MAP_KEY_ID, MAP_KEY_ATTRIBUTES);

  public static final TextAttributesKey VALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey(VALID_STRING_ESCAPE_ID, JavaHighlightingColors.VALID_STRING_ESCAPE);

  public static final TextAttributesKey INVALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey(INVALID_STRING_ESCAPE_ID, JavaHighlightingColors.INVALID_STRING_ESCAPE);

  public static final TextAttributesKey LABEL = TextAttributesKey.createTextAttributesKey(LABEL_ID, HighlighterColors.TEXT);


  private DefaultHighlighter() {
  }
}