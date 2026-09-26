// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.filters;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class GradleReRunBuildFilter implements Filter {

  // For a failed build with no options, you may see:
  //   "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output."
  // With --debug turned on, you may see:
  //   "<timestamp> [ERROR] [org.gradle.BuildExceptionReporter] Run with --stacktrace option to get the stack trace."
  // With --info turned on, you may see:
  //   "Run with --stacktrace option to get the stack trace. Run with --debug option to get more log output."
  // With --stacktrace turned on, you may see:
  //   "Run with --info or --debug option to get more log output."

  protected final String myBuildWorkingDir;
  private String line;
  private List<ResultItem> links;
  private int lineStart;

  public GradleReRunBuildFilter(String buildWorkingDir) {
    myBuildWorkingDir = buildWorkingDir;
  }

  @Override
  public Result applyFilter(@NotNull String line, int entireLength) {
    this.line = line;
    this.lineStart = entireLength - line.length();
    this.links = new ArrayList<>();
    String trimLine = line.trim();
    if (!(trimLine.contains("Run with --")
          && (trimLine.endsWith("option to get the stack trace.")
              || trimLine.endsWith("option to get more log output.")
              || trimLine.endsWith("to get full insights.")))) {
      return null;
    }
    addLinkIfMatch("Run with --stacktrace", "--stacktrace");
    addLinkIfMatch("Run with --info", "--info");
    addLinkIfMatch("Run with --debug option", "--debug");
    addLinkIfMatch("--debug option", "--debug");
    addLinkIfMatch("Run with --scan", "--scan");
    if (links.isEmpty()) {
      return null;
    }
    return new Result(links);
  }

  protected abstract @NotNull HyperlinkInfo getHyperLinkInfo(List<String> options);

  private void addLinkIfMatch(@NotNull String text, @NotNull String option) {
    int index = line.indexOf(text);
    if (index != -1) {
      links.add(createLink(lineStart + index, lineStart + index + text.length(), option));
    }
  }

  private @NotNull
  ResultItem createLink(int start, int end, @NotNull String option) {
    List<String> options = new ArrayList<>();
    options.add(option);
    return new ResultItem(start, end, getHyperLinkInfo(options));
  }
}
