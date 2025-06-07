// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.execution.GeneralCommandLineTest;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.ApplicationRule;
import org.jetbrains.annotations.NotNull;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;

public class PtyCommandLineTest extends GeneralCommandLineTest {
  public static final @ClassRule ApplicationRule appRule = new ApplicationRule();

  @Override
  protected @NotNull String filterExpectedOutput(@NotNull String output) {
    if (SystemInfo.isWindows) {
      output = StringUtil.trimTrailing(expandTabs(output, 8));
    }
    return output;
  }

  @Override
  protected GeneralCommandLine createCommandLine(String... command) {
    return new PtyCommandLine(Arrays.asList(command));
  }

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
