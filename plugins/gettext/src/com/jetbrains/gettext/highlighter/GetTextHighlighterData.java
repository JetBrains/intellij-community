package com.jetbrains.gettext.highlighter;

import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.TextAttributesKeyDefaults;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextHighlighterData {

  public static final String COMMENT_ID = "GET_TEXT_COMMENT";
  public static final TextAttributesKey COMMENT =
    TextAttributesKeyDefaults.createTextAttributesKey(COMMENT_ID, TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.LINE_COMMENT).clone());

  public static final String KEYWORD_ID = "GET_TEXT_KEYWORD";
  public static final TextAttributesKey KEYWORD =
    TextAttributesKeyDefaults.createTextAttributesKey(KEYWORD_ID, TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.KEYWORD).clone());

  public static final String STRING_ID = "GET_TEXT_STRING";
  public static final TextAttributesKey STRING =
    TextAttributesKeyDefaults.createTextAttributesKey(STRING_ID, TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.STRING).clone());

  public static final String FLAG_ID = "GET_TEXT_FLAG";
  public static final TextAttributesKey FLAG =
    TextAttributesKeyDefaults.createTextAttributesKey(FLAG_ID, TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.DOC_COMMENT_TAG).clone());

  public static final String NUMBER_ID = "GET_TEXT_TRANSLATED_NUMBER";
  public static final TextAttributesKey NUMBER =
    TextAttributesKeyDefaults.createTextAttributesKey(NUMBER_ID, TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.NUMBER).clone());

  public static final String BRACES_ID = "GET_TEXT_BRACES";
  public static final TextAttributesKey BRACES =
    TextAttributesKeyDefaults.createTextAttributesKey(BRACES_ID, TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.BRACES).clone());

  public static final String DOTS_ID = "GET_TEXT_DOTS";
  public static final TextAttributesKey DOTS =
    TextAttributesKeyDefaults.createTextAttributesKey(DOTS_ID, TextAttributesKeyDefaults.getDefaultAttributes(SyntaxHighlighterColors.DOT).clone());
}
