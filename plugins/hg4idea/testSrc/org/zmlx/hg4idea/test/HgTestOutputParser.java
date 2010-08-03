/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.zmlx.hg4idea.test;

import java.io.File;

/**
 * Helper class for parsing outputs of mercurial commands.
 * @author Kirill Likhodedov
 */
public class HgTestOutputParser {

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
