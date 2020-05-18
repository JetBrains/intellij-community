// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.ui.ClickListener;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;

public class BuildNumberStatusWidgetFactory implements StatusBarWidgetFactory {

  private static final String ID = "BuildNumber";

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return UIBundle.message("status.bar.build.number.widget.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project) {
    return true;
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return new BuildNumberStatusWidget();
  }

  @Override
  public void disposeWidget(@NotNull StatusBarWidget widget) {
  }

  @Override
  public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
    return true;
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  private static class BuildNumberStatusWidget extends TextPanel implements CustomStatusBarWidget {

    private BuildNumberStatusWidget() {
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent event, int clickCount) {
          CopyPasteManager.getInstance().setContents(new StringSelection(getBuildNumber()));
          return true;
        }
      }.installOn(this, true);
    }

    @Override
    public @NotNull String ID() {
      return ID;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
      setText(getBuildNumber());
    }

    @NotNull
    private static String getBuildNumber() {
      return ApplicationInfo.getInstance().getBuild().toString();
    }

    @Override
    public void dispose() {
    }

    @Override
    public JComponent getComponent() {
      return this;
    }
  }
}
