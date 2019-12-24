/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.progress;

import com.intellij.openapi.diagnostic.ControlFlowException;
import org.jetbrains.annotations.Nullable;

/**
 * An exception indicating that the currently running operation was terminated and should finish as soon as possible.
 * Normally this exception should not be caught, swallowed, logged or handled in any way.
 * It should be rethrown so that the infrastructure could handle it correctly.
 * This exception can happen during almost any IDE activity, e.g. any PSI query,
 * {@link com.intellij.openapi.extensions.ExtensionPointName#getExtensions},
 * {@link com.intellij.openapi.actionSystem.AnAction#update}, etc.<p></p>
 *
 * @see com.intellij.openapi.progress.ProgressIndicator#checkCanceled()
 * @see <a href="https://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html">General Threading Rules</a>
 */
public class ProcessCanceledException extends RuntimeException implements ControlFlowException {
  public ProcessCanceledException() { }

  public ProcessCanceledException(@Nullable Throwable cause) {
    super(cause);
    if (cause instanceof ProcessCanceledException) {
      throw new IllegalArgumentException("Must not self-wrap ProcessCanceledException: ", cause);
    }
  }
}
