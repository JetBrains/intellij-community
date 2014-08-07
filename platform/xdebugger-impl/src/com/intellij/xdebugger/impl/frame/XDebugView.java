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

import com.intellij.openapi.Disposable;
import com.intellij.util.SingleAlarm;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebugView implements Disposable {
  public enum SessionEvent {PAUSED, BEFORE_RESUME, RESUMED, STOPPED, FRAME_CHANGED, SETTINGS_CHANGED}

  private final SingleAlarm myClearAlarm;
  private static final int VIEW_CLEAR_DELAY = 100; //ms

  public XDebugView(@NotNull Disposable disposable) {
    myClearAlarm = new SingleAlarm(new Runnable() {
      @Override
      public void run() {
        clear();
      }
    }, VIEW_CLEAR_DELAY, disposable);
  }

  protected final void requestClear() {
    myClearAlarm.cancelAndRequest();
  }

  protected final void cancelClear() {
    myClearAlarm.cancel();
  }

  protected abstract void clear();

  public abstract void processSessionEvent(@NotNull SessionEvent event);
}
