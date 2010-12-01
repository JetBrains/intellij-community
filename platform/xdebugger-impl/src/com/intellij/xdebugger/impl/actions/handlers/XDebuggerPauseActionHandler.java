/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NotNull;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.openapi.actionSystem.DataContext;

/**
 * @author nik
*/
public class XDebuggerPauseActionHandler extends XDebuggerActionHandler {
  protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
    session.pause();
  }

  @Override
  public boolean isHidden(@NotNull Project project, AnActionEvent event) {
    final XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    return session == null || !((XDebugSessionImpl)session).isPauseActionSupported();
  }

  protected boolean isEnabled(@NotNull final XDebugSession session, final DataContext dataContext) {
    return !session.isPaused();
  }
}
