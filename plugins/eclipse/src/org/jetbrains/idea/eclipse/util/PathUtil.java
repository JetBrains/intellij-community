// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.util;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public final class PathUtil {
  @NotNull
  public static String normalize(@NotNull String path) {
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
      int slashIndex = path.substring(0, index).lastIndexOf('/');
      if (slashIndex < 0) break;
      path = path.substring(0, slashIndex) + path.substring(index + 3);
    }

    return path;
  }

  @NotNull
  public static String getRelative(@NotNull String baseRoot, @NotNull String path) {
    baseRoot = normalize(baseRoot);
    path = normalize(path);

    int prefix = findCommonPathPrefixLength(baseRoot, path);

    if (prefix != 0) {
      baseRoot = baseRoot.substring(prefix);
      path = path.substring(prefix);
      if (!baseRoot.isEmpty()) {
        return normalize(revertRelativePath(baseRoot.substring(1)) + path);
      }
      else if (!path.isEmpty()) {
        return path.substring(1);
      }
      else {
        return ".";
      }
    }
    if (FileUtil.isAbsolute(path)) {
      return path;
    }
    return normalize(revertRelativePath(baseRoot) + "/" + path);
  }

  private static int findCommonPathPrefixLength(@NotNull String path1, @NotNull String path2) {
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
    return Math.max(end, 0);
  }

  private static int endOfToken(String s, int index) {
    index = s.indexOf('/', index);
    return index == -1 ? s.length() : index;
  }

  @NotNull
  private static String revertRelativePath(@NotNull String path) {
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
