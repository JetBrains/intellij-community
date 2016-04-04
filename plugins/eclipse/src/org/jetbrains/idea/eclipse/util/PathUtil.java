/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.eclipse.util;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;

public class PathUtil {
  public static String normalize(String path) {
    path = FileUtil.toSystemIndependentName(path);
    path = StringUtil.trimEnd(path, "/");
    while (path.contains("/./")) {
      path = path.replace("/./", "/");
    }
    path = StringUtil.trimStart(path, "./");
    path = StringUtil.trimEnd(path, "/.");

    while (true) {
      int index = path.indexOf("/..");
      if (index < 0) break;
      int slashIndex = path.substring(0, index).lastIndexOf("/");
      if (slashIndex < 0) break;
      path = path.substring(0, slashIndex) + path.substring(index + 3);
    }

    return path;
  }

  public static String getRelative(String baseRoot, String path) {
    baseRoot = normalize(baseRoot);
    path = normalize(path);

    int prefix = findCommonPathPrefixLength(baseRoot, path);

    if (prefix != 0) {
      baseRoot = baseRoot.substring(prefix);
      path = path.substring(prefix);
      if (baseRoot.length() != 0) {
        return normalize(revertRelativePath(baseRoot.substring(1)) + path);
      }
      else if (path.length() != 0) {
        return path.substring(1);
      }
      else {
        return ".";
      }
    }
    else if (FileUtil.isAbsolute(path)) {
      return path;
    }
    else {
      return normalize(revertRelativePath(baseRoot) + "/" + path);
    }
  }

  public static int findCommonPathPrefixLength(String path1, String path2) {
    int end = -1;
    do {
      int beg = end + 1;
      int new_end = endOfToken(path1, beg);
      if (new_end != endOfToken(path2, beg) || !path1.substring(beg, new_end).equals(path2.substring(beg, new_end))) {
        break;
      }
      end = new_end;
    }
    while (end != path1.length());
    return end < 0 ? 0 : end;
  }

  private static int endOfToken(String s, int index) {
    index = s.indexOf("/", index);
    return (index == -1) ? s.length() : index;
  }

  private static String revertRelativePath(String path) {
    if (path.equals(".")) {
      return path;
    }
    else {
      StringBuilder sb = new StringBuilder();
      sb.append("..");
      int count = normalize(path).split("/").length;
      while (--count > 0) {
        sb.append("/..");
      }
      return sb.toString();
    }
  }

}
