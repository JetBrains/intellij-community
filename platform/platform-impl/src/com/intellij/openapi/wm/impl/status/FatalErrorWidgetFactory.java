// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.diagnostic.MessagePool;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;

public final class FatalErrorWidgetFactory implements StatusBarWidgetFactory, LightEditCompatible {
  @Override
  public @NotNull String getId() {
    return IdeMessagePanel.FATAL_ERROR;
  }

  @Override
  public @NotNull String getDisplayName() {
    return UIBundle.message("status.bar.fatal.error.widget.name");
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return new IdeMessagePanel(WindowManager.getInstance().getIdeFrame(project), MessagePool.getInstance());
  }

  @Override
  public boolean isConfigurable() {
    return false;
  }

  @Override
  public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
    return false;
  }
}
