/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class RelativePathCalculator {
  private static final int ourNumOfAllowedStepsAbove = 1;
  private static final int ourAllowedStepsDown = 2;

  private final String myShifted;
  private final String myBase;

  private boolean myRename;

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

  @NotNull
  public String execute() {
    if (stringEqual(myShifted, myBase)) {
      return ".";
    }
    final String[] baseParts = split(myBase);
    final String[] shiftedParts = split(myShifted);

    myRename = checkRename(baseParts, shiftedParts);

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
    if (!myRename && stepsUp > ourNumOfAllowedStepsAbove && shiftedParts.length - cnt <= ourAllowedStepsDown) {
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

  private boolean isRename() {
    return myRename;
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

  @Nullable
  public static String getMovedString(final String beforeName, final String afterName) {
    if (beforeName != null && afterName != null && !stringEqual(beforeName, afterName)) {
      final RelativePathCalculator calculator = new RelativePathCalculator(beforeName, afterName);
      String result = calculator.execute();
      final String key = calculator.isRename() ? "change.file.renamed.to.text" : "change.file.moved.to.text";
      return VcsBundle.message(key, result);
    }
    return null;
  }

  public static String[] split(final String s) {
    return s.replace(File.separatorChar, '/').split("/");
  }
}
