// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.util.Key;

public interface ProcessOutputTypes {
  /**
   * Please use {@code ProcessOutputType.SYSTEM} instead.
   */
  Key<?> SYSTEM = ProcessOutputType.SYSTEM;

  /**
   * Please use {@code ProcessOutputType.STDOUT} instead.
   *
   * Represents process standard output stream.<p>
   * Please note that stdout colored output type doesn't equal to this instance: use
   * <pre>{@code ProcessOutputType.isStdout(key)}</pre>
   * instead of
   * <pre>{@code ProcessOutputTypes.STDOUT.equals(key)} or ProcessOutputTypes.STDOUT == key</pre>
   */
  Key<?> STDOUT = ProcessOutputType.STDOUT;

  /**
   * Please use {@code ProcessOutputType.STDERR} instead.
   *
   * Represents process standard error stream.<p>
   * Please note that stderr colored output type doesn't equal to this instance: use
   * <pre>{@code ProcessOutputType.isStderr(key)}</pre>
   * instead of
   * <pre>{@code ProcessOutputTypes.STDERR.equals(key)} or ProcessOutputTypes.STDERR == key</pre>
   */
  Key<?> STDERR = ProcessOutputType.STDERR;
}