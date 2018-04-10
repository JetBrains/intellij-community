// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import org.jetbrains.annotations.NotNull;

/**
* @author nik
*/
public class XDebugViewSessionListener implements XDebugSessionListener {
  private final XDebugView myDebugView;
  private final XDebugSession mySession;

  private XDebugViewSessionListener(@NotNull XDebugView debugView, @NotNull XDebugSession session) {
    myDebugView = debugView;
    mySession = session;
  }

  public static void attach(@NotNull XDebugView debugView, @NotNull XDebugSession session) {
    session.addSessionListener(new XDebugViewSessionListener(debugView, session)); // do not use disposable here, all listeners are removed on session end
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
