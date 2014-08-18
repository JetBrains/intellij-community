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
package com.intellij.xdebugger.impl.frame;

import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.Alarm;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.EventObject;

/**
 * @author nik
 */
public abstract class XDebugView implements Disposable {
  public enum SessionEvent {PAUSED, BEFORE_RESUME, RESUMED, STOPPED, FRAME_CHANGED, SETTINGS_CHANGED}

  private final Alarm myUpdateAlarm;
  private static final int VIEW_UPDATE_DELAY = 100; //ms

  public XDebugView() {
    myUpdateAlarm = new Alarm(this);
  }

  protected abstract void clear();

  public void onSessionEvent(@NotNull final SessionEvent event) {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        processSessionEvent(event);
      }
    }, VIEW_UPDATE_DELAY);
  }

  protected abstract void processSessionEvent(@NotNull SessionEvent event);

  @Nullable
  protected static XDebugSession getSession(@NotNull EventObject e) {
    Component component = e.getSource() instanceof Component ? (Component)e.getSource() : null;
    return component == null ? null : getSession(component);
  }

  @Nullable
  public static XDebugSession getSession(@NotNull Component component) {
    DataContext dataContext = DataManager.getInstance().getDataContext(component);
    ViewContext viewContext = ViewContext.CONTEXT_KEY.getData(dataContext);
    ContentManager contentManager = viewContext == null ? null : viewContext.getContentManager();
    if (contentManager != null) {
      XDebugSession session = XDebugSessionTab.SESSION_KEY.getData(DataManager.getInstance().getDataContext(contentManager.getComponent()));
      if (session != null) {
        return session;
      }
    }
    return XDebugSessionTab.SESSION_KEY.getData(dataContext);
  }
}
