// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class RelativePathCalculator {
  private static final int ourNumOfAllowedStepsAbove = 1;
  private static final int ourAllowedStepsDown = 2;

  private final String myShifted;
  private final String myBase;

  public RelativePathCalculator(@NotNull String base, @NotNull String shifted) {
    myShifted = shifted;
    myBase = base;
  }

  private static boolean stringEqual(@NotNull String s1, @NotNull String s2) {
    if (!SystemInfo.isFileSystemCaseSensitive) {
      return s1.equalsIgnoreCase(s2);
    }
    return s1.equals(s2);
  }

  public @NlsSafe @NotNull String execute() {
    if (stringEqual(myShifted, myBase)) {
      return ".";
    }
    final String[] baseParts = split(myBase);
    final String[] shiftedParts = split(myShifted);

    boolean rename = checkRename(baseParts, shiftedParts);

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

    final int stepsUp = baseParts.length - cnt - 1;
    if (!rename && stepsUp > ourNumOfAllowedStepsAbove && shiftedParts.length - cnt <= ourAllowedStepsDown) {
      return myShifted;
    }
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < stepsUp; i++) {
      sb.append("../");
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

  private static boolean checkRename(final String[] baseParts, final String[] shiftedParts) {
    if (baseParts.length == shiftedParts.length) {
      for (int i = 0; i < baseParts.length; i++) {
        if (!stringEqual(baseParts[i], shiftedParts[i])) {
          return i == baseParts.length - 1;
        }
      }
    }
    return false;
  }

  public static String[] split(@NotNull String s) {
    return s.replace(File.separatorChar, '/').split("/");
  }
}
