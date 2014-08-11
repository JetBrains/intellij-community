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

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.util.SingleAlarm;
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

  private final SingleAlarm myClearAlarm;
  private static final int VIEW_CLEAR_DELAY = 100; //ms

  // used only to implement "clear"
  private volatile XDebugSession session;

  public XDebugView() {
    myClearAlarm = new SingleAlarm(new Runnable() {
      @Override
      public void run() {
        clear(session);
        session = null;
      }
    }, VIEW_CLEAR_DELAY, this);
  }

  protected final void requestClear(@NotNull XDebugSession session) {
    this.session = session;
    myClearAlarm.cancelAndRequest();
  }

  protected final void cancelClear() {
    session = null;
    myClearAlarm.cancel();
  }

  protected abstract void clear(@Nullable XDebugSession session);

  public abstract void processSessionEvent(@NotNull SessionEvent event, @NotNull XDebugSession session);

  @Nullable
  protected static XDebugSession getSession(@NotNull EventObject e) {
    Component component = e.getSource() instanceof Component ? (Component)e.getSource() : null;
    return component == null ? null : getSession(component);
  }

  @Nullable
  public static XDebugSession getSession(@NotNull Component component) {
    return XDebugSessionTab.SESSION_KEY.getData(DataManager.getInstance().getDataContext(component));
  }
}
