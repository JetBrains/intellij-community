// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ProcessOutput {
  private final StringBuilder myStdoutBuilder = new StringBuilder();
  private final StringBuilder myStderrBuilder = new StringBuilder();
  private @Nullable Integer myExitCode;
  private boolean myTimeout;
  private boolean myCancelled;

  public ProcessOutput() { }

  public ProcessOutput(int exitCode) {
    myExitCode = exitCode;
  }

  public void appendStdout(@Nullable String text) {
    myStdoutBuilder.append(text);
  }

  public void appendStderr(@Nullable String text) {
    myStderrBuilder.append(text);
  }

  public @NotNull @NlsSafe String getStdout() {
    return myStdoutBuilder.toString();
  }

  public @NotNull @NlsSafe String getStderr() {
    return myStderrBuilder.toString();
  }

  public @NotNull List<@NlsSafe String> getStdoutLines() {
    return getStdoutLines(true);
  }

  public @NotNull List<@NlsSafe String> getStdoutLines(boolean excludeEmptyLines) {
    return splitLines(getStdout(), excludeEmptyLines);
  }

  public @NotNull List<@NlsSafe String> getStderrLines() {
    return getStderrLines(true);
  }

  public @NotNull List<@NlsSafe String> getStderrLines(boolean excludeEmptyLines) {
    return splitLines(getStderr(), excludeEmptyLines);
  }

  private static List<String> splitLines(String s, boolean excludeEmptyLines) {
    String converted = StringUtil.convertLineSeparators(s);
    return StringUtil.split(converted, "\n", true, excludeEmptyLines);
  }

  /**
   * If exit code is nonzero or the process timed out, logs exit code and process output (if any) and returns {@code false},
   * otherwise just returns {@code true}.
   */
  public boolean checkSuccess(@NotNull Logger logger) {
    int ec = getExitCode();
    if (ec == 0 && !isTimeout()) {
      return true;
    }

    logger.info(isTimeout() ? "Timed out" : "Exit code " + ec);

    String output = getStderr();
    if (output.isEmpty()) output = getStdout();
    if (!output.isEmpty()) {
      logger.info(output);
    }

    return false;
  }

  public void setExitCode(int exitCode) {
    myExitCode = exitCode;
  }

  public int getExitCode() {
    Integer code = myExitCode;
    return code == null ? -1 : code;
  }

  /**
   * Returns {@code false} if exit code wasn't set (e.g. when {@code CapturingProcessHandler.runProcess()} execution was interrupted).
   */
  public boolean isExitCodeSet() {
    return myExitCode != null;
  }

  public void setTimeout() {
    myTimeout = true;
  }

  public boolean isTimeout() {
    return myTimeout;
  }

  public void setCancelled() {
    myCancelled = true;
  }

  public boolean isCancelled() {
    return myCancelled;
  }
}