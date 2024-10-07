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
package com.intellij.xdebugger.impl.actions;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.ThreadsActionsProvider;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class DebuggerThreadActionHandler extends DebuggerActionHandler {

  public static final DebuggerThreadActionHandler FreezeActiveThread = new DebuggerThreadActionHandler(provider -> provider.getFreezeActiveThreadHandler());
  public static final DebuggerThreadActionHandler ThawActiveThread = new DebuggerThreadActionHandler(provider -> provider.getThawActiveThreadHandler());
  public static final DebuggerThreadActionHandler FreezeInactiveThreads = new DebuggerThreadActionHandler(provider -> provider.getFreezeInactiveThreadsHandler());
  public static final DebuggerThreadActionHandler ThawAllThreads = new DebuggerThreadActionHandler(provider -> provider.getThawAllThreadsHandler());

  public static final DebuggerThreadActionHandler FreezeInactiveThreadsAmongSelected = new DebuggerThreadActionHandler(provider -> provider.getFreezeInactiveThreadsAmongSelectedHandler());
  public static final DebuggerThreadActionHandler FreezeSelectedThreads = new DebuggerThreadActionHandler(provider -> provider.getFreezeSelectedThreads());
  public static final DebuggerThreadActionHandler ThawSelectedThreads = new DebuggerThreadActionHandler(provider -> provider.getThawSelectedThreads());

  private final Function<ThreadsActionsProvider, DebuggerActionHandler> myGetHandler;

  private DebuggerThreadActionHandler(Function<ThreadsActionsProvider, DebuggerActionHandler> getHandler) {
    myGetHandler = getHandler;
  }

  @Override
  public void perform(@NotNull Project project, @NotNull AnActionEvent event) {
    XDebugSession session = DebuggerUIUtil.getSession(event);
    if (session != null) {
      if (session.getDebugProcess() instanceof ThreadsActionsProvider) {
        var handler = myGetHandler.apply((ThreadsActionsProvider)session.getDebugProcess());
        if (handler != null) {
          handler.perform(project, event);
        }
      }
    }
  }

  @Override
  public boolean isEnabled(@NotNull Project project, @NotNull AnActionEvent event) {
    if (LightEdit.owns(project)) return false;
    XDebugSession session = DebuggerUIUtil.getSession(event);
    if (session != null) {
      if (session.getDebugProcess() instanceof ThreadsActionsProvider) {
        var handler = myGetHandler.apply((ThreadsActionsProvider)session.getDebugProcess());
        if (handler != null) {
          return handler.isEnabled(project, event);
        }
      }
    }
    return false;
  }

  @Override
  public boolean isHidden(@NotNull Project project, @NotNull AnActionEvent event) {
    XDebugSession session = DebuggerUIUtil.getSession(event);
    if (session != null) {
      if (session.getDebugProcess() instanceof ThreadsActionsProvider) {
        var handler = myGetHandler.apply((ThreadsActionsProvider)session.getDebugProcess());
        if (handler != null) {
          return handler.isHidden(project, event);
        }
      }
    }
    return true;
  }
}
