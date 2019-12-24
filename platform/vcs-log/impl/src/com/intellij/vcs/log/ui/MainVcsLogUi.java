// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui;

import com.intellij.openapi.util.Condition;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EventListener;

@ApiStatus.Experimental
public interface MainVcsLogUi extends VcsLogUiEx {
  @NotNull
  @Override
  VcsLogFilterUiEx getFilterUi();

  @NotNull
  JComponent getToolbar();

  @NotNull
  @Override
  MainVcsLogUiProperties getProperties();

  void invokeOnChange(@NotNull Runnable runnable,
                      @NotNull Condition<? super VcsLogDataPack> condition);

  void addFilterListener(@NotNull VcsLogFilterListener listener);

  interface VcsLogFilterListener extends EventListener {
    void onFiltersChanged();
  }
}
