// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.util.paths;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

final class FilePathHashUtil {
  public static int pathHashCode(boolean caseSensitive, @NotNull String path) {
    return pathHashCode(caseSensitive, path, 0, path.length(), 0);
  }

  public static int pathHashCode(boolean caseSensitive, @NotNull String path, int offset1, int offset2, int prefixHash) {
    if (caseSensitive) {
      return StringUtil.stringHashCode(path, offset1, offset2, prefixHash);
    }
    else {
      return StringUtil.stringHashCodeInsensitive(path, offset1, offset2, prefixHash);
    }
  }
}
