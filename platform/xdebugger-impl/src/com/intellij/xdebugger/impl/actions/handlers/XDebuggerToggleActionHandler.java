/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XDebuggerToggleActionHandler extends DebuggerToggleActionHandler {
  @Override
  public final boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    return isEnabled(session, event);
  }

  @Override
  public boolean isSelected(@NotNull final Project project, final AnActionEvent event) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    return isSelected(session, event);
  }

  @Override
  public void setSelected(@NotNull final Project project, final AnActionEvent event, final boolean state) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    setSelected(session, event, state);
  }

  protected abstract boolean isEnabled(@Nullable XDebugSession session, final AnActionEvent event);

  protected abstract boolean isSelected(@Nullable XDebugSession session, final AnActionEvent event);

  protected abstract void setSelected(@Nullable XDebugSession session, final AnActionEvent event, boolean state);
}
