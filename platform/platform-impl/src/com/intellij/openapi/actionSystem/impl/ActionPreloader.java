// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

final class ActionPreloader extends PreloadingActivity {
  @Override
  public void preload(@NotNull ProgressIndicator indicator) {
    if (!ApplicationManagerEx.getApplicationEx().isLightEditMode()) {
      ((ActionManagerImpl)ActionManager.getInstance()).preloadActions(indicator);
    }
  }
}
