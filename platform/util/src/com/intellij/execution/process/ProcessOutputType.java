// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a stream (stdout/stderr/system) output type. It can be a base output type or a colored output type.
 * Base stdout/stderr output types are constants: {@link ProcessOutputTypes#STDOUT}, {@link ProcessOutputTypes#STDERR} and
 * {@link ProcessOutputTypes#SYSTEM}.<br/>
 * A colored stdout/stderr output type corresponds to a unique ANSI color attributes info passed as
 * {@code name} constructor parameter, and base stream output type (stdout/stderr) - {@code streamType} parameter.
 * <p/>
 * Use {@link com.intellij.execution.ui.ConsoleViewContentType#getConsoleViewType} to get TextAttributes for an instance
 * of this class.
 * <p/>
 * @see com.intellij.execution.process.ColoredOutputTypeRegistry
 * @see com.intellij.execution.ui.ConsoleViewContentType#registerNewConsoleViewType
 */
public class ProcessOutputType extends Key<Object> {
  public static final ProcessOutputType SYSTEM = new ProcessOutputType("system");

  /**
   * Base type for data from process standard output stream.<p>
   * Colored texts from stdout are represented by different instances of {@link ProcessOutputType} having
   * {@code ProcessOutputType.STDOUT} base type:
   * <pre>{@code coloredStdoutType.getBaseOutputType() == ProcessOutputType.STDOUT}</pre>
   * <p/>
   * Thus, to check whether a process output type is from stdout:
   * <pre>{@code ProcessOutputType.isStdout(key)}</pre>
   * instead of
   * <pre>{@code ProcessOutputType.STDOUT.equals(key)} or ProcessOutputType.STDOUT == key</pre>
   */
  public static final ProcessOutputType STDOUT = new ProcessOutputType("stdout");

  /**
   * Base type for data from process standard error stream.<p>
   * Colored texts from stderr are represented by different instances of {@link ProcessOutputType} having
   * {@code ProcessOutputType.STDERR} base type:
   * <pre>{@code coloredStderrType.getBaseOutputType() == ProcessOutputType.STDERR}</pre>
   * <p/>
   * Thus, to check whether a process output type is from stderr:
   * <pre>{@code ProcessOutputType.isStderr(key)}</pre>
   * instead of
   * <pre>{@code ProcessOutputType.STDERR.equals(key)} or ProcessOutputType.STDERR == key</pre>
   */
  public static final ProcessOutputType STDERR = new ProcessOutputType("stderr");

  private final @Nullable String myEscapeSequence;
  private final @NotNull ProcessOutputType myStreamType;

  public ProcessOutputType(@NotNull String name, @NotNull ProcessOutputType streamType) {
    super(name);
    myEscapeSequence = name;
    myStreamType = streamType.getBaseOutputType();
  }

  private ProcessOutputType(@NotNull String name) {
    super(name);
    myEscapeSequence = null;
    myStreamType = this;
  }

  public @NotNull ProcessOutputType getBaseOutputType() {
    return myStreamType;
  }

  public boolean isStdout() {
    return getBaseOutputType() == STDOUT;
  }

  public boolean isStderr() {
    return getBaseOutputType() == STDERR;
  }

  public static boolean isStderr(@NotNull Key<?> key) {
    return key instanceof ProcessOutputType && ((ProcessOutputType)key).isStderr();
  }

  public static boolean isStdout(@NotNull Key<?> key) {
    return key instanceof ProcessOutputType && ((ProcessOutputType)key).isStdout();
  }

  public @Nullable String getEscapeSequence() {
    return myEscapeSequence;
  }

  @Override
  public String toString() {
    if (myEscapeSequence != null) {
      return "[" + getBaseOutputType() + "] " + toHumanReadableEscapeSequence(myEscapeSequence);
    }
    return super.toString();
  }

  private static @NotNull String toHumanReadableEscapeSequence(@NotNull String escapeSequence) {
    return escapeSequence.replace("\u001b", "ESC")
      .replace("\u0007", "BEL")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\b", "\\b");
  }

  /**
   * Converts one of the keys from @{@link ProcessOutputTypes} to {@link ProcessOutputType}.
   * Throws an IllegalArgumentException if the key is not one of ProcessOutputTypes.
   */
  public static @NotNull ProcessOutputType fromKey(@NotNull Key<?> key) {
    if (key instanceof ProcessOutputType) {
      return (ProcessOutputType)key;
    }
    throw new IllegalArgumentException("Unexpected key " + key + ". Should be one of ProcessOutputTypes");
  }
}
