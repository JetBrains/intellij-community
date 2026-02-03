// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;

public final class FilePathsHelper {
  private FilePathsHelper() {
  }

  public static @NlsSafe String convertPath(final FilePath fp) {
    return convertPath(fp.getPath());
  }

  public static @NlsSafe String convertWithLastSeparator(final FilePath fp) {
    return convertWithLastSeparatorImpl(fp.getPath(), fp.isDirectory());
  }

  private static @NlsSafe String convertWithLastSeparatorImpl(final String initPath, final boolean isDir) {
    final String path = isDir ? (initPath.endsWith("/") || initPath.endsWith("\\") ? initPath : initPath + "/") : initPath;
    return convertPath(path);
  }

  public static @NlsSafe String convertPath(final String parent, final String subpath) {
    String convParent = FileUtil.toSystemIndependentName(parent);
    String convPath = FileUtil.toSystemIndependentName(subpath);

    String withSlash = StringUtil.trimEnd(convParent, "/") + "/" + StringUtil.trimStart(convPath, "/");
    return SystemInfo.isFileSystemCaseSensitive ? withSlash : StringUtil.toUpperCase(withSlash);
  }

  public static @NotNull @NlsSafe String convertPath(@NotNull String s) {
    String result = FileUtil.toSystemIndependentName(s);
    return SystemInfo.isFileSystemCaseSensitive ? result : StringUtil.toUpperCase(result);
  }
}
