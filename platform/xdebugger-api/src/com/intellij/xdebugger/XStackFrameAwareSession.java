/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.xdebugger;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debugger session aware of stack frame
 * 
 * @author traff
 */
public interface XStackFrameAwareSession extends AbstractDebuggerSession {
  Project getProject();
  
  void addSessionListener(XDebugSessionListener listener);
  void removeSessionListener(XDebugSessionListener listener);

  @NotNull
  XDebuggerEditorsProvider getEditorsProvider();

  @Nullable
  XStackFrame getCurrentStackFrame();

  void setCurrentStackFrame(@NotNull XExecutionStack executionStack, @NotNull XStackFrame frame);

  void reportError(String message);

  @Nullable
  XSuspendContext getSuspendContext();
}
