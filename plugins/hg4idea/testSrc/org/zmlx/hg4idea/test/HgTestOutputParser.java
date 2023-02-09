// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.test;

import java.io.File;

/**
 * Helper class for parsing outputs of mercurial commands.
 * @author Kirill Likhodedov
 */
public final class HgTestOutputParser {

  public static String added(String... path) {
    return "A " + path(path);
  }

  public static String removed(String... path) {
    return "R " + path(path);
  }

  public static String unknown(String... path) {
    return "? " + path(path);
  }

  public static String modified(String... path) {
    return "M " + path(path);
  }

  public static String missing(String... path) {
    return "! " + path(path);
  }

  public static String path(String... line) {
    StringBuilder builder = new StringBuilder();

    int linePartCount = line.length;

    for (int i = 0; i < linePartCount; i++) {
      String linePart = line[i];
      builder.append(linePart);

      if (i < linePartCount - 1) {
        builder.append(File.separator);
      }
    }

    return builder.toString();
  }
}
