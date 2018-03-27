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
 * Represents a stream (stdout/stderr/system) output type. It can be a base output type or a colored output type.
 * Base stdout/stderr output types are constants: {@link ProcessOutputTypes#STDOUT}, {@link ProcessOutputTypes#STDERR} and
 * {@link ProcessOutputTypes#SYSTEM}.<br/>
 * A colored stdout/stderr output type corresponds to an unique ANSI color attributes info passed as
 * {@code name} constructor parameter, and base stream output type (stdout/stderr) - {@code streamType} parameter.
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
  public ProcessOutputType getBaseOutputType() {
    return myStreamType != null ? myStreamType : this;
  }

  public boolean isStdout() {
    return getBaseOutputType() == ProcessOutputTypes.STDOUT;
  }

  public boolean isStderr() {
    return getBaseOutputType() == ProcessOutputTypes.STDERR;
  }

  public static boolean isStderr(@NotNull Key key) {
    return key instanceof ProcessOutputType && ((ProcessOutputType)key).isStderr();
  }

  public static boolean isStdout(@NotNull Key key) {
    return key instanceof ProcessOutputType && ((ProcessOutputType)key).isStdout();
  }

  @Nullable
  public static ProcessOutputType tryCast(@NotNull Key key) {
    return key instanceof ProcessOutputType ? (ProcessOutputType)key : null;
  }
}
