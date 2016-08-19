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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.TimerListener;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

/**
 * @author Vladimir Kondratyev
 */
public class WeakTimerListener implements TimerListener {
  private final ActionManagerEx myManager;
  private final Reference<TimerListener> myRef;

  public WeakTimerListener(@NotNull ActionManagerEx manager, @NotNull TimerListener delegate) {
    myManager = manager;
    myRef = new WeakReference<>(delegate);
  }

  @Override
  public ModalityState getModalityState() {
    TimerListener delegate = myRef.get();
    if (delegate != null) {
      return delegate.getModalityState();
    }
    else{
      myManager.removeTimerListener(this);
      return null;
    }
  }

  @Override
  public void run() {
    TimerListener delegate = myRef.get();
    if (delegate != null) {
      delegate.run();
    }
  }
}
