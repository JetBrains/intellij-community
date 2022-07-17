// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.TimerListener;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

/** @deprecated use {@link com.intellij.openapi.actionSystem.ActionManager#removeTimerListener(TimerListener)} instead */
@Deprecated(forRemoval = true)
public class WeakTimerListener implements TimerListener {
  private final Reference<TimerListener> myRef;

  public WeakTimerListener(@NotNull TimerListener delegate) {
    myRef = new WeakReference<>(delegate);
  }

  @Override
  public ModalityState getModalityState() {
    TimerListener delegate = myRef.get();
    if (delegate != null) {
      return delegate.getModalityState();
    }
    else {
      ActionManagerEx.getInstanceEx().removeTimerListener(this);
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
