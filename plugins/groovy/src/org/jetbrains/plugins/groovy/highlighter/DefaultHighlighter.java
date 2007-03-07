package org.jetbrains.plugins.groovy.highlighter;

import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.HighlighterColors;

/**
 *
 * @author Ilya.Sergey
 */
public class DefaultHighlighter {

  @NonNls
  private static final String LINE_COMMENT_ID = "GROOVY_LINE_COMMENT";
  @NonNls
  private static final String BLOCK_COMMENT_ID = "GROOVY_BLOCK_COMMENT";
  @NonNls
  private static final String KEYWORD_ID = "GROOVY_KEYWORD";
  @NonNls
  private static final String NUMBER_ID = "GROOVY_NUMBER";
  @NonNls
  private static final String STRING_ID = "GROOVY_STRING";
  @NonNls
  private static final String REGEXP_ID = "GROOVY_REGEXP";
  @NonNls
  private static final String BRACKETS_ID = "GROOVY_BRACKETS";

  @NonNls
  private static final String OPERATION_SIGN_ID = "GROOVY_OPERATION_SIGN";
  @NonNls
  private static final String BAD_CHARACTER_ID = "GROOVY_BAD_CHARACTER";


  // Registering TextAttributes
  static {
    TextAttributesKey.createTextAttributesKey(LINE_COMMENT_ID, HighlighterColors.JAVA_LINE_COMMENT.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(BLOCK_COMMENT_ID, HighlighterColors.JAVA_BLOCK_COMMENT.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(KEYWORD_ID, HighlighterColors.JAVA_KEYWORD.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(NUMBER_ID, HighlighterColors.JAVA_NUMBER.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(STRING_ID, HighlighterColors.JAVA_STRING.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(REGEXP_ID, HighlighterColors.JAVA_VALID_STRING_ESCAPE.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(BRACKETS_ID, HighlighterColors.JAVA_BRACKETS.getDefaultAttributes());

    TextAttributesKey.createTextAttributesKey(OPERATION_SIGN_ID, HighlighterColors.JAVA_OPERATION_SIGN.getDefaultAttributes());
    TextAttributesKey.createTextAttributesKey(BAD_CHARACTER_ID, HighlighterColors.BAD_CHARACTER.getDefaultAttributes());
  }

  public static TextAttributesKey LINE_COMMENT = TextAttributesKey.createTextAttributesKey(LINE_COMMENT_ID);
  public static TextAttributesKey BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey(BLOCK_COMMENT_ID);
  public static TextAttributesKey KEYWORD = TextAttributesKey.createTextAttributesKey(KEYWORD_ID);
  public static TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey(NUMBER_ID);
  public static TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey(STRING_ID);
  public static TextAttributesKey REGEXP = TextAttributesKey.createTextAttributesKey(REGEXP_ID);
  public static TextAttributesKey BRACES = TextAttributesKey.createTextAttributesKey(BRACKETS_ID);

  public static TextAttributesKey OPERATION_SIGN = TextAttributesKey.createTextAttributesKey(OPERATION_SIGN_ID);
  public static TextAttributesKey BAD_CHARACTER = TextAttributesKey.createTextAttributesKey(BAD_CHARACTER_ID);

}