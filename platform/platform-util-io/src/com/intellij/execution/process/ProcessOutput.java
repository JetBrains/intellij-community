// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class ProcessOutput {
  private final StringBuilder myStdoutBuilder;
  private final StringBuilder myStderrBuilder;
  private volatile @Nullable Integer myExitCode;
  private volatile boolean myTimeout;
  private volatile boolean myCancelled;

  public ProcessOutput() {
    this("", "", null, false, false);
  }

  public ProcessOutput(int exitCode) {
    this("", "", exitCode, false, false);
  }

  public ProcessOutput(@NotNull String stdout, @NotNull String stderr, int exitCode, boolean timeout, boolean cancelled) {
    this(stdout, stderr, Integer.valueOf(exitCode), timeout, cancelled);
  }

  private ProcessOutput(@NotNull String stdout, @NotNull String stderr, @Nullable Integer exitCode, boolean timeout, boolean cancelled) {
    myStdoutBuilder = new StringBuilder(stdout);
    myStderrBuilder = new StringBuilder(stderr);
    myExitCode = exitCode;
    myTimeout = timeout;
    myCancelled = cancelled;
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

  public @Unmodifiable @NotNull List<@NlsSafe String> getStdoutLines() {
    return getStdoutLines(true);
  }

  public @Unmodifiable @NotNull List<@NlsSafe String> getStdoutLines(boolean excludeEmptyLines) {
    return splitLines(getStdout(), excludeEmptyLines);
  }

  public @Unmodifiable @NotNull List<@NlsSafe String> getStderrLines() {
    return getStderrLines(true);
  }

  public @Unmodifiable @NotNull List<@NlsSafe String> getStderrLines(boolean excludeEmptyLines) {
    return splitLines(getStderr(), excludeEmptyLines);
  }

  private static @Unmodifiable List<String> splitLines(String s, boolean excludeEmptyLines) {
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

  @Override
  public String toString() {
    return "{" +
           "exitCode=" + myExitCode +
           ", timeout=" + myTimeout +
           ", cancelled=" + myCancelled +
           ", stdout=" + myStdoutBuilder +
           ", stderr=" + myStderrBuilder +
           '}';
  }
}