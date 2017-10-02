/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a stream (stdout/stderr) output type. It can be a base output type or colored output type.
 * Base stdout/stderr output types is constants: {@link ProcessOutputTypes#STDOUT} and
 * {@link ProcessOutputTypes#STDERR}.<br/>
 * A colored stdout/stderr output type is created one per each unique color attributes info decoded as
 * {@code name} parameter, and base stream output type (stdout/stderr) - {@code streamType} parameter.
 * <p/>
 * Use {@link com.intellij.execution.ui.ConsoleViewContentType#getConsoleViewType} to get TextAttributes for an instance
 * of this class.
 * <p/>
 * @see {@link com.intellij.execution.process.ColoredOutputTypeRegistry}
 * @see {@link com.intellij.execution.ui.ConsoleViewContentType#registerNewConsoleViewType}
 */
@SuppressWarnings({"JavaDoc", "JavadocReference"})
public class ProcessOutputType extends Key {
  private final ProcessOutputType myStreamType;

  public ProcessOutputType(@NotNull String name, @NotNull ProcessOutputType streamType) {
    super(name);
    myStreamType = streamType;
  }

  ProcessOutputType(@NotNull String name) {
    super(name);
    myStreamType = null;
  }

  @NotNull
  private ProcessOutputType getStreamType() {
    return myStreamType != null ? myStreamType : this;
  }

  public boolean isStdout() {
    return getStreamType() == ProcessOutputTypes.STDOUT;
  }

  public boolean isStderr() {
    return getStreamType() == ProcessOutputTypes.STDERR;
  }

  public static boolean isStderr(@NotNull Key key) {
    if (key instanceof ProcessOutputType) {
      return ((ProcessOutputType)key).isStderr();
    }
    return false;
  }

  public static boolean isStdout(@NotNull Key key) {
    if (key instanceof ProcessOutputType) {
      return ((ProcessOutputType)key).isStdout();
    }
    return false;
  }

  @Nullable
  public static ProcessOutputType tryCast(@NotNull Key key) {
    return key instanceof ProcessOutputType ? (ProcessOutputType)key : null;
  }
}
