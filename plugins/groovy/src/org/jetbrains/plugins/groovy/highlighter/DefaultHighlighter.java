/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.TextAttributesKeyDefaults;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
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
  static final String WRONG_STRING_ID = "Wrong string literal";

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
  static final String LITERAL_CONVERSION_ID = "List/map to object conversion";

  static final String VALID_STRING_ESCAPE_ID = "Valid string escape";
  static final String INVALID_STRING_ESCAPE_ID = "Invalid string escape";

  public static TextAttributesKey LINE_COMMENT = TextAttributesKeyDefaults.createTextAttributesKey(LINE_COMMENT_ID,
                                                                                                   TextAttributesKeyDefaults
                                                                                                     .getDefaultAttributes(
                                                                                                       SyntaxHighlighterColors.LINE_COMMENT));

  public static TextAttributesKey ANNOTATION = TextAttributesKeyDefaults.createTextAttributesKey(ANNOTATION_ID,
                                                                                                 TextAttributesKeyDefaults
                                                                                                   .getDefaultAttributes(
                                                                                                     HighlightInfoType.ANNOTATION_NAME
                                                                                                       .getAttributesKey()));

  public static TextAttributesKey LOCAL_VARIABLE = TextAttributesKeyDefaults.createTextAttributesKey("Groovy var",
                                                                                                     TextAttributesKeyDefaults
                                                                                                       .getDefaultAttributes(
                                                                                                         HighlightInfoType.LOCAL_VARIABLE
                                                                                                           .getAttributesKey()));
  public static TextAttributesKey REASSIGNED_LOCAL_VARIABLE = TextAttributesKeyDefaults.createTextAttributesKey("Groovy reassigned var",
                                                                                                                TextAttributesKeyDefaults
                                                                                                                  .getDefaultAttributes(
                                                                                                                    HighlightInfoType
                                                                                                                      .REASSIGNED_LOCAL_VARIABLE
                                                                                                                      .getAttributesKey()));
  public static TextAttributesKey PARAMETER = TextAttributesKeyDefaults.createTextAttributesKey("Groovy parameter",
                                                                                                TextAttributesKeyDefaults
                                                                                                  .getDefaultAttributes(
                                                                                                    HighlightInfoType.PARAMETER
                                                                                                      .getAttributesKey()));
  public static TextAttributesKey REASSIGNED_PARAMETER = TextAttributesKeyDefaults.createTextAttributesKey("Groovy reassigned parameter",
                                                                                                           TextAttributesKeyDefaults
                                                                                                             .getDefaultAttributes(
                                                                                                               HighlightInfoType
                                                                                                                 .REASSIGNED_PARAMETER
                                                                                                                 .getAttributesKey()));

  public static TextAttributesKey METHOD_DECLARATION = TextAttributesKeyDefaults.createTextAttributesKey("Groovy method declaration",
                                                                                                         TextAttributesKeyDefaults
                                                                                                           .getDefaultAttributes(
                                                                                                             HighlightInfoType
                                                                                                               .METHOD_DECLARATION
                                                                                                               .getAttributesKey()));

  public static TextAttributesKey INSTANCE_FIELD = TextAttributesKeyDefaults.createTextAttributesKey(INSTANCE_FIELD_ID,
                                                                                                     TextAttributesKeyDefaults
                                                                                                       .getDefaultAttributes(
                                                                                                         HighlightInfoType.INSTANCE_FIELD
                                                                                                           .getAttributesKey()));

  public static TextAttributesKey METHOD_CALL = TextAttributesKeyDefaults.createTextAttributesKey(METHOD_CALL_ID,
                                                                                                  TextAttributesKeyDefaults
                                                                                                    .getDefaultAttributes(
                                                                                                      HighlightInfoType.METHOD_CALL
                                                                                                        .getAttributesKey()));

  public static TextAttributesKey STATIC_FIELD = TextAttributesKeyDefaults.createTextAttributesKey(STATIC_FIELD_ID,
                                                                                                   TextAttributesKeyDefaults
                                                                                                     .getDefaultAttributes(
                                                                                                       HighlightInfoType.STATIC_FIELD
                                                                                                         .getAttributesKey()));

  public static TextAttributesKey STATIC_METHOD_ACCESS = TextAttributesKeyDefaults.createTextAttributesKey(STATIC_METHOD_ACCESS_ID,
                                                                                                           TextAttributesKeyDefaults
                                                                                                             .getDefaultAttributes(
                                                                                                               HighlightInfoType
                                                                                                                 .STATIC_METHOD
                                                                                                                 .getAttributesKey()));

  public static TextAttributesKey BLOCK_COMMENT = TextAttributesKeyDefaults.createTextAttributesKey(BLOCK_COMMENT_ID,
                                                                                                    TextAttributesKeyDefaults
                                                                                                      .getDefaultAttributes(
                                                                                                        SyntaxHighlighterColors.JAVA_BLOCK_COMMENT));

  public static TextAttributesKey DOC_COMMENT_CONTENT = TextAttributesKeyDefaults.createTextAttributesKey(DOC_COMMENT_ID,
                                                                                                          TextAttributesKeyDefaults
                                                                                                            .getDefaultAttributes(
                                                                                                              SyntaxHighlighterColors.DOC_COMMENT));

  public static TextAttributesKey DOC_COMMENT_TAG = TextAttributesKeyDefaults.createTextAttributesKey(DOC_COMMENT_TAG_ID,
                                                                                                      TextAttributesKeyDefaults
                                                                                                        .getDefaultAttributes(
                                                                                                          SyntaxHighlighterColors.DOC_COMMENT_TAG));

  public static TextAttributesKey CLASS_REFERENCE =
    TextAttributesKeyDefaults
      .createTextAttributesKey(CLASS_REFERENCE_ID, TextAttributesKeyDefaults.getDefaultAttributes(HighlighterColors.TEXT).clone());

  public static TextAttributesKey TYPE_PARAMETER =
    TextAttributesKeyDefaults.createTextAttributesKey(TYPE_PARAMETER_ID, TextAttributesKeyDefaults
      .getDefaultAttributes(CodeInsightColors.TYPE_PARAMETER_NAME_ATTRIBUTES).clone());

  public static final TextAttributes INSTANCE_PROPERTY_REFERENCE_ATTRIBUTES = TextAttributesKeyDefaults.getDefaultAttributes(INSTANCE_FIELD).clone();
  public static final TextAttributes STATIC_PROPERTY_REFERENCE_ATTRIBUTES = TextAttributesKeyDefaults.getDefaultAttributes(STATIC_FIELD).clone();
  static {
    INSTANCE_PROPERTY_REFERENCE_ATTRIBUTES.setFontType(Font.PLAIN);
    STATIC_PROPERTY_REFERENCE_ATTRIBUTES.setFontType(Font.ITALIC);
  }
  public static TextAttributesKey INSTANCE_PROPERTY_REFERENCE =
    TextAttributesKeyDefaults.createTextAttributesKey(INSTANCE_PROPERTY_REFERENCE_ID, INSTANCE_PROPERTY_REFERENCE_ATTRIBUTES);

  public static TextAttributesKey STATIC_PROPERTY_REFERENCE =
    TextAttributesKeyDefaults.createTextAttributesKey(STATIC_PROPERTY_REFERENCE_ID, STATIC_PROPERTY_REFERENCE_ATTRIBUTES);

  public static final TextAttributes KEYWORD_ATTRIBUTES = TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.KEYWORD).clone();
  static{

    KEYWORD_ATTRIBUTES.setForegroundColor(new Color(0, 0, 67));
    KEYWORD_ATTRIBUTES.setFontType(Font.BOLD);
  }

  public static TextAttributesKey KEYWORD = TextAttributesKeyDefaults
    .createTextAttributesKey("GROOVY_" + KEYWORD_ID.toUpperCase(), KEYWORD_ATTRIBUTES);

  public static TextAttributesKey NUMBER = TextAttributesKeyDefaults.createTextAttributesKey(NUMBER_ID,
                                                                                             TextAttributesKeyDefaults.getDefaultAttributes(
                                                                                               SyntaxHighlighterColors.NUMBER));

  public static TextAttributesKey GSTRING = TextAttributesKeyDefaults.createTextAttributesKey(GSTRING_ID,
                                                                                              TextAttributesKeyDefaults
                                                                                                .getDefaultAttributes(
                                                                                                  SyntaxHighlighterColors.STRING));

  public static TextAttributesKey STRING = TextAttributesKeyDefaults.createTextAttributesKey(STRING_ID,
                                                                                             TextAttributesKeyDefaults.getDefaultAttributes(
                                                                                               SyntaxHighlighterColors.STRING));

  public static TextAttributesKey BRACES = TextAttributesKeyDefaults.createTextAttributesKey(BRACES_ID,
                                                                                             TextAttributesKeyDefaults.getDefaultAttributes(
                                                                                               SyntaxHighlighterColors.BRACES));

  public static TextAttributesKey BRACKETS = TextAttributesKeyDefaults.createTextAttributesKey(BRACKETS_ID,
                                                                                               TextAttributesKeyDefaults
                                                                                                 .getDefaultAttributes(
                                                                                                   SyntaxHighlighterColors.BRACKETS));

  public static TextAttributesKey PARENTHESES = TextAttributesKeyDefaults.createTextAttributesKey(PARENTHESES_ID,
                                                                                                  TextAttributesKeyDefaults
                                                                                                    .getDefaultAttributes(
                                                                                                      SyntaxHighlighterColors.PARENTHS));

  public static TextAttributesKey OPERATION_SIGN = TextAttributesKeyDefaults.createTextAttributesKey(OPERATION_SIGN_ID,
                                                                                                     TextAttributesKeyDefaults
                                                                                                       .getDefaultAttributes(
                                                                                                         SyntaxHighlighterColors.OPERATION_SIGN));

  public static TextAttributesKey BAD_CHARACTER = TextAttributesKeyDefaults.createTextAttributesKey(BAD_CHARACTER_ID,
                                                                                                    TextAttributesKeyDefaults
                                                                                                      .getDefaultAttributes(
                                                                                                        CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES));

  public static TextAttributesKey WRONG_STRING = TextAttributesKeyDefaults.createTextAttributesKey(WRONG_STRING_ID,
                                                                                                   TextAttributesKeyDefaults
                                                                                                     .getDefaultAttributes(
                                                                                                       SyntaxHighlighterColors.STRING));


  public static final TextAttributes UNRESOLVED_ACCESS_ATTRIBUTES = TextAttributesKeyDefaults.getDefaultAttributes(HighlighterColors.TEXT).clone();
  static{
    UNRESOLVED_ACCESS_ATTRIBUTES.setForegroundColor(Color.BLACK);
    UNRESOLVED_ACCESS_ATTRIBUTES.setEffectColor(Color.GRAY);
    UNRESOLVED_ACCESS_ATTRIBUTES.setEffectType(EffectType.LINE_UNDERSCORE);
  }
  public static final TextAttributes LITERAL_CONVERSION_ATTRIBUTES = TextAttributesKeyDefaults.getDefaultAttributes(HighlighterColors.TEXT).clone();
  static{
    LITERAL_CONVERSION_ATTRIBUTES.setForegroundColor(Color.BLUE);
    LITERAL_CONVERSION_ATTRIBUTES.setFontType(Font.BOLD);
  }

  public static final TextAttributes MAP_KEY_ATTRIBUTES = TextAttributesKeyDefaults.getDefaultAttributes(HighlighterColors.TEXT).clone();

  public static final Color MAP_KEY_COLOR = new Color(0, 128, 0);

  static {
    MAP_KEY_ATTRIBUTES.setForegroundColor(MAP_KEY_COLOR);
  }
  public static TextAttributesKey UNRESOLVED_ACCESS = TextAttributesKeyDefaults
    .createTextAttributesKey(UNRESOLVED_ACCESS_ID, UNRESOLVED_ACCESS_ATTRIBUTES);
  public static TextAttributesKey LITERAL_CONVERSION = TextAttributesKeyDefaults
    .createTextAttributesKey(LITERAL_CONVERSION_ID, LITERAL_CONVERSION_ATTRIBUTES);

  public static TextAttributesKey MAP_KEY = TextAttributesKeyDefaults.createTextAttributesKey(MAP_KEY_ID, MAP_KEY_ATTRIBUTES);

  public static final TextAttributesKey VALID_STRING_ESCAPE =
    TextAttributesKeyDefaults.createTextAttributesKey(VALID_STRING_ESCAPE_ID, TextAttributesKeyDefaults
      .getDefaultAttributes(SyntaxHighlighterColors.VALID_STRING_ESCAPE));
  public static final TextAttributesKey INVALID_STRING_ESCAPE = TextAttributesKeyDefaults
    .createTextAttributesKey(INVALID_STRING_ESCAPE_ID, TextAttributesKeyDefaults
      .getDefaultAttributes(SyntaxHighlighterColors.INVALID_STRING_ESCAPE));

  private DefaultHighlighter() {
  }
}