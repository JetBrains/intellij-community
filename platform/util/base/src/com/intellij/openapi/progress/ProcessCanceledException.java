// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.openapi.diagnostic.ControlFlowException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CancellationException;

/**
 * An exception indicating that the currently running operation was terminated and should finish as soon as possible.
 * <p>
 * Usually, this exception should not be caught, swallowed, logged, or handled in any way.
 * Instead, it should be rethrown so that the infrastructure can handle it correctly.
 * </p>
 * <p>
 * This exception can happen during almost any IDE activity, e.g. any PSI query,
 * {@link com.intellij.openapi.extensions.ExtensionPointName#getExtensions},
 * {@link com.intellij.openapi.actionSystem.AnAction#update}, etc.
 * </p>
 *
 * @see com.intellij.openapi.progress.ProgressIndicator#checkCanceled()
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/general-threading-rules.html">General Threading Rules</a>
 */
public class ProcessCanceledException extends CancellationException implements ControlFlowException {
  public ProcessCanceledException() { }

  public ProcessCanceledException(@Nullable Throwable cause) {
    super(cause == null ? null : cause.toString()); // repeat Throwable(Throwable) constructor logic
    if (cause instanceof ProcessCanceledException) {
      throw new IllegalArgumentException("Must not self-wrap ProcessCanceledException: ", cause);
    }
    initCause(cause);
  }

  protected ProcessCanceledException(@NotNull String message) {
    super(message);
  }
}
