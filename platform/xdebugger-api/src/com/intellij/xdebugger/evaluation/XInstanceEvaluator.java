/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xdebugger.evaluation;

import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.NotNull;

/**
 * Subclass to allow to recalculate instance value in a different context
 *
 * @author egor
 */
public interface XInstanceEvaluator {
  /**
   * Recalculates instance value in the context of the provided stack frame
   * @see XDebuggerEvaluator
   */
  void evaluate(@NotNull XDebuggerEvaluator.XEvaluationCallback callback, @NotNull XStackFrame frame);
}
