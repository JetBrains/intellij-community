// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame;

import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.SingleAlarm;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.EventObject;

public abstract class XDebugView implements Disposable {
  public enum SessionEvent {PAUSED, BEFORE_RESUME, RESUMED, STOPPED, FRAME_CHANGED, SETTINGS_CHANGED}

  private final SingleAlarm clearAlarm = SingleAlarm.Companion.singleEdtAlarm(100, this, () -> clear());

  protected final void requestClear() {
    // no delay in tests
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (!clearAlarm.isDisposed()) {
        clear();
      }
    }
    else {
      clearAlarm.cancelAndRequest();
    }
  }

  public @Nullable JComponent getMainComponent() {
    return null;
  }

  protected final void cancelClear() {
    clearAlarm.cancel();
  }

  protected abstract void clear();

  @ApiStatus.OverrideOnly
  @ApiStatus.Internal
  public void processSessionEvent(@NotNull SessionEvent event, @NotNull XDebugSessionProxy session) {
    if (session instanceof XDebugSessionProxy.Monolith monolith) {
      processSessionEvent(event, monolith.getSession());
    }
  }

  /**
   * Use {@link XDebugView#processSessionEvent(SessionEvent, XDebugSessionProxy)} instead
   */
  @ApiStatus.Obsolete
  public void processSessionEvent(@NotNull SessionEvent event, @NotNull XDebugSession session) {
    processSessionEvent(event, XDebugSessionProxyKeeper.getInstance(session.getProject()).getOrCreateProxy(session));
  }

  protected static @Nullable XDebugSession getSession(@NotNull EventObject e) {
    Component component = e.getSource() instanceof Component ? (Component)e.getSource() : null;
    return component == null ? null : getSession(component);
  }

  public static @Nullable XDebugSession getSession(@NotNull Component component) {
    return getData(XDebugSession.DATA_KEY, component);
  }

  public static @Nullable <T> T getData(DataKey<T> key, @NotNull Component component) {
    DataContext dataContext = DataManager.getInstance().getDataContext(component);
    ViewContext viewContext = ViewContext.CONTEXT_KEY.getData(dataContext);
    ContentManager contentManager = viewContext == null ? null : viewContext.getContentManager();
    if (contentManager != null && !contentManager.isDisposed()) {
      T data = key.getData(DataManager.getInstance().getDataContext(contentManager.getComponent()));
      if (data != null) {
        return data;
      }
    }
    return key.getData(dataContext);
  }
}
