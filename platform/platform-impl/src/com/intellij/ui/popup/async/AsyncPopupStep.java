// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.async;

import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.SpeedSearchFilter;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;

/**
 * Use this "mock" popup step when you need long processing (I/O, network activity etc.) to build real one.
 * The real example is a list of processes on remote host you can connect to with debugger.
 * This step would be started as 'callable' on pooled thread as soon as AsyncPopupImpl instance with "Loading..." text is being created
 * When real popup step is obtained from the background task, mock item would be automatically replaced with it.
 */
public abstract class AsyncPopupStep implements PopupStep, Callable<PopupStep> {
  @Nullable
  @Override
  public String getTitle() {
    return null;
  }

  @Nullable
  @Override
  public PopupStep onChosen(Object selectedValue, boolean finalChoice) {
    return null;
  }

  @Override
  public boolean hasSubstep(Object selectedValue) {
    return false;
  }

  @Override
  public void canceled() {

  }

  @Override
  public boolean isMnemonicsNavigationEnabled() {
    return false;
  }

  @Nullable
  @Override
  public MnemonicNavigationFilter getMnemonicNavigationFilter() {
    return null;
  }

  @Override
  public boolean isSpeedSearchEnabled() {
    return false;
  }

  @Nullable
  @Override
  public SpeedSearchFilter getSpeedSearchFilter() {
    return null;
  }

  @Override
  public boolean isAutoSelectionEnabled() {
    return false;
  }

  @Nullable
  @Override
  public Runnable getFinalRunnable() {
    return null;
  }
}
