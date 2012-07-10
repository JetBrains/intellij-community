package com.jetbrains.gettext;

import com.intellij.openapi.application.PathManager;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextUtils {

  public static String[] getAllTestedFiles() {
    return new String[]{
      "simple",
      "without_comments",
      "flags",
      "complex_flags",
      "msg_plural",
      "range_flag",
      "string"};
  }

  protected static String getDataSubpath() {
    return "community/plugins/gettext/test/com/jetbrains/gettext/lexer";
  }

  public static String getFullPath(final String fileName) {
    return PathManager.getHomePath() + "/" + getDataSubpath() + "/" + fileName;
  }

  public static String getFullParserResultPath(final String fileName) {
    return getFullPath(fileName) + "_parser.txt";
  }
}