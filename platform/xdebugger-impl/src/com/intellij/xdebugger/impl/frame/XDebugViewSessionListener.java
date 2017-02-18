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

  public XDebugViewSessionListener(@NotNull XDebugView debugView, @NotNull XDebugSession session) {
    myDebugView = debugView;
    mySession = session;
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
}
