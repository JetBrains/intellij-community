// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;

public class PtyCommandLineTest extends GeneralCommandLineTest {
  @NotNull
  @Override
  protected String filterExpectedOutput(@NotNull String output) {
    if (SystemInfo.isWindows) {
      output = StringUtil.trimTrailing(expandTabs(output, 8));
    }
    return output;
  }

  @Override
  protected GeneralCommandLine createCommandLine(String... command) {
    return new PtyCommandLine(Arrays.asList(command));
  }

  @NotNull
  private static String expandTabs(String s, @SuppressWarnings("SameParameterValue") int tabSize) {
    StringBuilder sb = new StringBuilder();

    int col = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\t') {
        int tabWidth = tabSize - col % tabSize;
        StringUtil.repeatSymbol(sb, ' ', tabWidth);
        col += tabWidth;
      }
      else {
        sb.append(c);
        col = StringUtil.isLineBreak(c) ? 0 : (col + 1);
      }
    }

    return sb.toString();
  }

  @Ignore @Test @Override
  public void redirectInput() { }
}