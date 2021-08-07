// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit;

import com.intellij.execution.ConsoleFolding;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class FailedTestDebugLogConsoleFolding extends ConsoleFolding {
  @Override
  public boolean shouldFoldLine(@NotNull Project project, @NotNull String line) {
    return line.length() > 0 && line.charAt(line.length() - 1) == '\u2003';  // `TestLoggerFactory#FAILED_TEST_DEBUG_OUTPUT_MARKER`
  }

  @Override
  public String getPlaceholderText(@NotNull Project project, @NotNull List<String> lines) {
    return " <DEBUG log>";
  }
}
