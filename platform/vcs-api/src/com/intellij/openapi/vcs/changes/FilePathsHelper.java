/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;

public class FilePathsHelper {
  private FilePathsHelper() {
  }

  public static String convertPath(final FilePath fp) {
    return convertPath(fp.getPath());
  }

  public static String convertWithLastSeparator(final FilePath fp) {
    return convertWithLastSeparatorImpl(fp.getPath(), fp.isDirectory());
  }

  private static String convertWithLastSeparatorImpl(final String initPath, final boolean isDir) {
    final String path = isDir ? (initPath.endsWith("/") || initPath.endsWith("\\") ? initPath : initPath + "/") : initPath;
    return convertPath(path);
  }

  public static String convertPath(final String parent, final String subpath) {
    String convParent = FileUtil.toSystemIndependentName(parent);
    String convPath = FileUtil.toSystemIndependentName(subpath);

    String withSlash = StringUtil.trimEnd(convParent, "/") + "/" + StringUtil.trimStart(convPath, "/");
    return SystemInfo.isFileSystemCaseSensitive ? withSlash : withSlash.toUpperCase();
  }

  @NotNull
  public static String convertPath(@NotNull String s) {
    String result = FileUtil.toSystemIndependentName(s);
    return SystemInfo.isFileSystemCaseSensitive ? result : result.toUpperCase();
  }
}
