// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class XDebugViewSessionListener implements XDebugSessionListener {
  private final XDebugView myDebugView;
  private final XDebugSessionProxy mySession;

  private XDebugViewSessionListener(@NotNull XDebugView debugView, @NotNull XDebugSessionProxy session) {
    myDebugView = debugView;
    mySession = session;
  }

  /**
   * Use {@link #attach(XDebugView, XDebugSessionProxy)} instead.
   */
  @ApiStatus.Obsolete
  public static void attach(@NotNull XDebugView debugView, @NotNull XDebugSession session) {
    XDebugSessionProxy proxy = XDebugSessionProxyKeeperKt.asProxy(session);
    attach(debugView, proxy);
  }

  @ApiStatus.Internal
  public static void attach(@NotNull XDebugView debugView, @NotNull XDebugSessionProxy session) {
    session.addSessionListener(new XDebugViewSessionListener(debugView, session), debugView);
  }

  private void onSessionEvent(@NotNull XDebugView.SessionEvent event) {
    myDebugView.processSessionEvent(event, mySession);
  }

  @Override
  public void sessionPaused() {
    onSessionEvent(XDebugView.SessionEvent.PAUSED);
  }

  @Override
  public void sessionResumed() {
    onSessionEvent(XDebugView.SessionEvent.RESUMED);
  }

  @Override
  public void sessionStopped() {
    onSessionEvent(XDebugView.SessionEvent.STOPPED);
  }

  @Override
  public void stackFrameChanged() {
    onSessionEvent(XDebugView.SessionEvent.FRAME_CHANGED);
  }

  @Override
  public void beforeSessionResume() {
    onSessionEvent(XDebugView.SessionEvent.BEFORE_RESUME);
  }

  @Override
  public void settingsChanged() {
    onSessionEvent(XDebugView.SessionEvent.SETTINGS_CHANGED);
  }
}
