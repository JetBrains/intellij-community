/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.openapi.editor.HighlighterColors;
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
  static final String LINE_COMMENT_ID = "line comments";
  @NonNls
  static final String BLOCK_COMMENT_ID = "block comments";
  @NonNls
  static final String KEYWORD_ID = "keywords";
  @NonNls
  static final String NUMBER_ID = "numbers";
  @NonNls
  static final String STRING_ID = "strings";
  @NonNls
  static final String REGEXP_ID = "regular expressions";
  @NonNls
  static final String BRACES_ID = "braces";

  @NonNls
  static final String OPERATION_SIGN_ID = "operation signs";
  @NonNls
  static final String BAD_CHARACTER_ID = "bad character";
  @NonNls
  static final String WRONG_STRING_ID = "wrong construction";

  @NonNls
  static final String UNTYPED_ACCESS_ID = "untyped access";

  public static TextAttributesKey LINE_COMMENT = TextAttributesKey.createTextAttributesKey(LINE_COMMENT_ID,
      HighlighterColors.JAVA_LINE_COMMENT.getDefaultAttributes());

  public static TextAttributesKey BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey(BLOCK_COMMENT_ID,
      HighlighterColors.JAVA_BLOCK_COMMENT.getDefaultAttributes());

  public static TextAttributesKey KEYWORD = TextAttributesKey.createTextAttributesKey(KEYWORD_ID,
      HighlighterColors.JAVA_KEYWORD.getDefaultAttributes());

  public static TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey(NUMBER_ID,
      HighlighterColors.JAVA_NUMBER.getDefaultAttributes());

  public static TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey(STRING_ID,
      HighlighterColors.JAVA_STRING.getDefaultAttributes());

  public static TextAttributesKey REGEXP = TextAttributesKey.createTextAttributesKey(REGEXP_ID,
      HighlighterColors.JAVA_VALID_STRING_ESCAPE.getDefaultAttributes());

  public static TextAttributesKey BRACES = TextAttributesKey.createTextAttributesKey(BRACES_ID,
      HighlighterColors.JAVA_BRACKETS.getDefaultAttributes());

  public static TextAttributesKey OPERATION_SIGN = TextAttributesKey.createTextAttributesKey(OPERATION_SIGN_ID,
      HighlighterColors.JAVA_OPERATION_SIGN.getDefaultAttributes());

  public static TextAttributesKey BAD_CHARACTER = TextAttributesKey.createTextAttributesKey(BAD_CHARACTER_ID,
      CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES.getDefaultAttributes());

  public static TextAttributesKey WRONG_STRING = TextAttributesKey.createTextAttributesKey(WRONG_STRING_ID,
      HighlighterColors.JAVA_STRING.getDefaultAttributes());

  public static TextAttributesKey UNTYPED_ACCESS = TextAttributesKey.createTextAttributesKey(UNTYPED_ACCESS_ID,
      new TextAttributes(HighlighterColors.JAVA_BRACKETS.getDefaultAttributes().getForegroundColor(), null,
          HighlighterColors.JAVA_BRACKETS.getDefaultAttributes().getForegroundColor(), EffectType.LINE_UNDERSCORE, Font.PLAIN));
}