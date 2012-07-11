package com.jetbrains.gettext;

import com.intellij.openapi.application.PathManager;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextUtils {
  private static final String path = "community/plugins/gettext/test/com/jetbrains/gettext/";

  public static String[] getAllTestedFiles() {
    return new String[]{
      "simple",
      "without_comments",
      "flags",
      "complex_flags",
      "msg_plural",
      "range_flag",
      "string",
      "command_format",
      "multi_id",
      "command"};
  }

  private static String getFullPath() {
    return PathManager.getHomePath() + "/" + path;
  }

  public static String getFullSourcePath(final String fileName) {
    return getFullPath() + "lexer/" + fileName + ".po";
  }

  public static String getFullLexerPath(final String fileName) {
    return getFullPath() + "lexer/" + fileName + ".txt";
  }

  public static String getFullParserPath(final String fileName) {
    return getFullPath() + "parser/" + fileName + ".txt";
  }
}