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

  public static TextAttributesKey LINE_COMMENT = TextAttributesKey.createTextAttributesKey(LINE_COMMENT_ID,
                                                                                           SyntaxHighlighterColors.LINE_COMMENT
                                                                                             .getDefaultAttributes());

  public static TextAttributesKey ANNOTATION = TextAttributesKey.createTextAttributesKey(ANNOTATION_ID,
                                                                                         HighlightInfoType.ANNOTATION_NAME
                                                                                           .getAttributesKey().getDefaultAttributes());

  public static TextAttributesKey LOCAL_VARIABLE = TextAttributesKey.createTextAttributesKey("Groovy var",
                                                                                             HighlightInfoType.LOCAL_VARIABLE
                                                                                               .getAttributesKey().getDefaultAttributes());
  public static TextAttributesKey REASSIGNED_LOCAL_VARIABLE = TextAttributesKey.createTextAttributesKey("Groovy reassigned var",
                                                                                                        HighlightInfoType
                                                                                                          .REASSIGNED_LOCAL_VARIABLE
                                                                                                          .getAttributesKey()
                                                                                                          .getDefaultAttributes());
  public static TextAttributesKey PARAMETER = TextAttributesKey.createTextAttributesKey("Groovy parameter",
                                                                                        HighlightInfoType.PARAMETER
                                                                                          .getAttributesKey().getDefaultAttributes());
  public static TextAttributesKey REASSIGNED_PARAMETER = TextAttributesKey.createTextAttributesKey("Groovy reassigned parameter",
                                                                                                   HighlightInfoType
                                                                                                     .REASSIGNED_PARAMETER
                                                                                                     .getAttributesKey()
                                                                                                     .getDefaultAttributes());

  public static TextAttributesKey METHOD_DECLARATION = TextAttributesKey.createTextAttributesKey("Groovy method declaration",
                                                                                                 HighlightInfoType
                                                                                                   .METHOD_DECLARATION
                                                                                                   .getAttributesKey()
                                                                                                   .getDefaultAttributes());

  public static TextAttributesKey INSTANCE_FIELD = TextAttributesKey.createTextAttributesKey(INSTANCE_FIELD_ID,
                                                                                             HighlightInfoType.INSTANCE_FIELD
                                                                                               .getAttributesKey().getDefaultAttributes());

  public static TextAttributesKey METHOD_CALL = TextAttributesKey.createTextAttributesKey(METHOD_CALL_ID,
                                                                                          HighlightInfoType.METHOD_CALL
                                                                                            .getAttributesKey().getDefaultAttributes());

  public static TextAttributesKey STATIC_FIELD = TextAttributesKey.createTextAttributesKey(STATIC_FIELD_ID,
                                                                                           HighlightInfoType.STATIC_FIELD
                                                                                             .getAttributesKey().getDefaultAttributes());

  public static TextAttributesKey STATIC_METHOD_ACCESS = TextAttributesKey.createTextAttributesKey(STATIC_METHOD_ACCESS_ID,
                                                                                                   HighlightInfoType
                                                                                                     .STATIC_METHOD
                                                                                                     .getAttributesKey()
                                                                                                     .getDefaultAttributes());

  public static TextAttributesKey BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey(BLOCK_COMMENT_ID,
                                                                                            SyntaxHighlighterColors.JAVA_BLOCK_COMMENT
                                                                                              .getDefaultAttributes());

  public static TextAttributesKey DOC_COMMENT_CONTENT = TextAttributesKey.createTextAttributesKey(DOC_COMMENT_ID,
                                                                                                  SyntaxHighlighterColors.DOC_COMMENT
                                                                                                    .getDefaultAttributes());

  public static TextAttributesKey DOC_COMMENT_TAG = TextAttributesKey.createTextAttributesKey(DOC_COMMENT_TAG_ID,
                                                                                              SyntaxHighlighterColors.DOC_COMMENT_TAG
                                                                                                .getDefaultAttributes());

  public static TextAttributesKey CLASS_REFERENCE =
    TextAttributesKey
      .createTextAttributesKey(CLASS_REFERENCE_ID, HighlighterColors.TEXT.getDefaultAttributes().clone());

  public static TextAttributesKey TYPE_PARAMETER =
    TextAttributesKey.createTextAttributesKey(TYPE_PARAMETER_ID, CodeInsightColors.TYPE_PARAMETER_NAME_ATTRIBUTES.getDefaultAttributes()
      .clone());

  public static final TextAttributes INSTANCE_PROPERTY_REFERENCE_ATTRIBUTES = INSTANCE_FIELD.getDefaultAttributes().clone();
  public static final TextAttributes STATIC_PROPERTY_REFERENCE_ATTRIBUTES = STATIC_FIELD.getDefaultAttributes().clone();

  static {
    INSTANCE_PROPERTY_REFERENCE_ATTRIBUTES.setFontType(Font.PLAIN);
    STATIC_PROPERTY_REFERENCE_ATTRIBUTES.setFontType(Font.ITALIC);
  }
  public static TextAttributesKey INSTANCE_PROPERTY_REFERENCE =
    TextAttributesKey.createTextAttributesKey(INSTANCE_PROPERTY_REFERENCE_ID, INSTANCE_PROPERTY_REFERENCE_ATTRIBUTES);

  public static TextAttributesKey STATIC_PROPERTY_REFERENCE =
    TextAttributesKey.createTextAttributesKey(STATIC_PROPERTY_REFERENCE_ID, STATIC_PROPERTY_REFERENCE_ATTRIBUTES);

  public static final TextAttributes KEYWORD_ATTRIBUTES = SyntaxHighlighterColors.KEYWORD.getDefaultAttributes().clone();

  static{

    KEYWORD_ATTRIBUTES.setForegroundColor(new Color(0, 0, 67));
    KEYWORD_ATTRIBUTES.setFontType(Font.BOLD);
  }

  public static TextAttributesKey KEYWORD = TextAttributesKey
    .createTextAttributesKey("GROOVY_" + KEYWORD_ID.toUpperCase(), KEYWORD_ATTRIBUTES);

  public static TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey(NUMBER_ID,
                                                                                     SyntaxHighlighterColors.NUMBER.getDefaultAttributes());

  public static TextAttributesKey GSTRING = TextAttributesKey.createTextAttributesKey(GSTRING_ID,
                                                                                      SyntaxHighlighterColors.STRING.getDefaultAttributes());

  public static TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey(STRING_ID,
                                                                                     SyntaxHighlighterColors.STRING.getDefaultAttributes());

  public static TextAttributesKey BRACES = TextAttributesKey.createTextAttributesKey(BRACES_ID,
                                                                                     SyntaxHighlighterColors.BRACES.getDefaultAttributes());

  public static TextAttributesKey BRACKETS = TextAttributesKey.createTextAttributesKey(BRACKETS_ID,
                                                                                       SyntaxHighlighterColors.BRACKETS
                                                                                         .getDefaultAttributes());

  public static TextAttributesKey PARENTHESES = TextAttributesKey.createTextAttributesKey(PARENTHESES_ID,
                                                                                          SyntaxHighlighterColors.PARENTHS
                                                                                            .getDefaultAttributes());

  public static TextAttributesKey OPERATION_SIGN = TextAttributesKey.createTextAttributesKey(OPERATION_SIGN_ID,
                                                                                             SyntaxHighlighterColors.OPERATION_SIGN
                                                                                               .getDefaultAttributes());

  public static TextAttributesKey BAD_CHARACTER = TextAttributesKey.createTextAttributesKey(BAD_CHARACTER_ID,
                                                                                            CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES
                                                                                              .getDefaultAttributes());

  public static TextAttributesKey WRONG_STRING = TextAttributesKey.createTextAttributesKey(WRONG_STRING_ID,
                                                                                           SyntaxHighlighterColors.STRING
                                                                                             .getDefaultAttributes());


  public static final TextAttributes UNRESOLVED_ACCESS_ATTRIBUTES = HighlighterColors.TEXT.getDefaultAttributes().clone();

  static{
    UNRESOLVED_ACCESS_ATTRIBUTES.setForegroundColor(Color.BLACK);
    UNRESOLVED_ACCESS_ATTRIBUTES.setEffectColor(Color.GRAY);
    UNRESOLVED_ACCESS_ATTRIBUTES.setEffectType(EffectType.LINE_UNDERSCORE);
  }
  public static final TextAttributes LITERAL_CONVERSION_ATTRIBUTES = HighlighterColors.TEXT.getDefaultAttributes().clone();

  static{
    LITERAL_CONVERSION_ATTRIBUTES.setForegroundColor(Color.BLUE);
    LITERAL_CONVERSION_ATTRIBUTES.setFontType(Font.BOLD);
  }

  public static final TextAttributes MAP_KEY_ATTRIBUTES = HighlighterColors.TEXT.getDefaultAttributes().clone();

  public static final Color MAP_KEY_COLOR = new Color(0, 128, 0);

  static {
    MAP_KEY_ATTRIBUTES.setForegroundColor(MAP_KEY_COLOR);
  }
  public static TextAttributesKey UNRESOLVED_ACCESS = TextAttributesKey
    .createTextAttributesKey(UNRESOLVED_ACCESS_ID, UNRESOLVED_ACCESS_ATTRIBUTES);
  public static TextAttributesKey LITERAL_CONVERSION = TextAttributesKey
    .createTextAttributesKey(LITERAL_CONVERSION_ID, LITERAL_CONVERSION_ATTRIBUTES);

  public static TextAttributesKey MAP_KEY = TextAttributesKey.createTextAttributesKey(MAP_KEY_ID, MAP_KEY_ATTRIBUTES);

  public static final TextAttributesKey VALID_STRING_ESCAPE =
    TextAttributesKey.createTextAttributesKey(VALID_STRING_ESCAPE_ID, SyntaxHighlighterColors.VALID_STRING_ESCAPE.getDefaultAttributes());
  public static final TextAttributesKey INVALID_STRING_ESCAPE = TextAttributesKey
    .createTextAttributesKey(INVALID_STRING_ESCAPE_ID, SyntaxHighlighterColors.INVALID_STRING_ESCAPE.getDefaultAttributes());

  private DefaultHighlighter() {
  }
}