// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;

@ApiStatus.Internal
public final class RelativePathCalculator {
  private static final int MAX_STEPS_UP = 3; // max depth of ../../../../../../ chain
  private static final int MAX_STEPS_DOWN = 4; // max depth of ./very/deep/path/to/shifted/directory/

  private static boolean stringEqual(@NotNull String s1, @NotNull String s2) {
    if (!SystemInfo.isFileSystemCaseSensitive) {
      return s1.equalsIgnoreCase(s2);
    }
    return s1.equals(s2);
  }

  @Contract("_, _, false -> !null")
  public static @NlsSafe @SystemIndependent String computeRelativePath(@NotNull @SystemIndependent String base,
                                                                       @NotNull @SystemIndependent String shifted,
                                                                       boolean relativePathsOnly) {
    if (stringEqual(shifted, base)) {
      return ".";
    }
    String[] baseParts = base.split("/");
    String[] shiftedParts = shifted.split("/");

    int cnt = 0;
    while (true) {
      if (baseParts.length <= cnt || shiftedParts.length <= cnt) {
        // means that directory moved to a file or vise versa -> undo last match
        cnt--;
        break;
      }
      if (!stringEqual(baseParts[cnt], shiftedParts[cnt])) {
        break;
      }
      ++cnt;
    }

    int stepsUp = baseParts.length - cnt - 1;
    int stepsDown = shiftedParts.length - cnt - 1;

    if (stepsDown > MAX_STEPS_DOWN) {
      return relativePathsOnly ? null : shifted;
    }

    StringBuilder sb = new StringBuilder();
    if (stepsUp < MAX_STEPS_UP) {
      for (int i = 0; i < stepsUp; i++) {
        sb.append("../");
      }
    }
    else {
      sb.append(StringUtil.ELLIPSIS + "/");
    }

    for (int i = cnt; i < shiftedParts.length - 1; i++) {
      final String shiftedPart = shiftedParts[i];
      sb.append(shiftedPart);
      sb.append('/');
    }

    String oldName = baseParts[baseParts.length - 1];
    String newName = shiftedParts[shiftedParts.length - 1];
    boolean skipFileName = oldName.equals(newName);
    if (!skipFileName) {
      sb.append(newName);
    }

    return sb.toString();
  }

  public static String[] split(@NotNull String s) {
    return s.replace(File.separatorChar, '/').split("/");
  }
}
