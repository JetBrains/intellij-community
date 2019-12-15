// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class ProcessOutput {
  private final StringBuilder myStdoutBuilder = new StringBuilder();
  private final StringBuilder myStderrBuilder = new StringBuilder();
  @Nullable private Integer myExitCode;
  private boolean myTimeout;
  private boolean myCancelled;

  public ProcessOutput() {
  }

  public ProcessOutput(final int exitCode) {
    myExitCode = exitCode;
  }

  public void appendStdout(@Nullable String text) {
    myStdoutBuilder.append(text);
  }

  public void appendStderr(@Nullable String text) {
    myStderrBuilder.append(text);
  }

  @NotNull
  public String getStdout() {
    return myStdoutBuilder.toString();
  }

  @NotNull
  public String getStderr() {
    return myStderrBuilder.toString();
  }

  @NotNull
  public List<String> getStdoutLines() {
    return getStdoutLines(true);
  }

  @NotNull
  public List<String> getStdoutLines(boolean excludeEmptyLines) {
    return splitLines(getStdout(), excludeEmptyLines);
  }

  @NotNull
  public List<String> getStderrLines() {
    return getStderrLines(true);
  }

  @NotNull
  public List<String> getStderrLines(boolean excludeEmptyLines) {
    return splitLines(getStderr(), excludeEmptyLines);
  }

  @NotNull
  private static List<String> splitLines(String s, boolean excludeEmptyLines) {
    String converted = StringUtil.convertLineSeparators(s);
    return StringUtil.split(converted, "\n", true, excludeEmptyLines);
  }

  /**
   * If exit code is nonzero or the process timed out, logs stderr and exit code and returns false,
   * else just returns true.
   *
   * @param logger where to put error information
   * @return true iff exit code is zero
   */
  public boolean checkSuccess(@NotNull final Logger logger) {
    if (getExitCode() != 0 || isTimeout()) {
      logger.info(getStderr() + (isTimeout()? "\nTimed out" : "\nExit code " + getExitCode()));
      return false;
    }
    return true;
  }

  public void setExitCode(int exitCode) {
    myExitCode = exitCode;
  }

  public int getExitCode() {
    Integer code = myExitCode;
    return code == null ? -1 : code;
  }

  /**
   * @return false if exit code wasn't set,
   * for example, when our CapturingProcessHandler.runProcess() is interrupted)
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
