/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * User: spLeaner
 */
public class ProcessIconWidget implements CustomStatusBarWidget {
  private AsyncProcessIcon myRefreshIcon;
  private EmptyIcon myEmptyRefreshIcon;

  public ProcessIconWidget() {
    myRefreshIcon = new AsyncProcessIcon("Refreshing filesystem") {
      protected Icon getPassiveIcon() {
        return myEmptyRefreshIcon;
      }

      @Override
      public Dimension getPreferredSize() {
        if (!isRunning()) return new Dimension(4, 0);
        return super.getPreferredSize();
      }
    };

    myRefreshIcon.setBorder(StatusBarWidget.WidgetBorder.INSTANCE);
    myRefreshIcon.setOpaque(false);

    myEmptyRefreshIcon = new EmptyIcon(0 /*myRefreshIcon.getPreferredSize().width*/, myRefreshIcon.getPreferredSize().height);
    setRefreshVisible(false);
  }

  public JComponent getComponent() {
    return myRefreshIcon;
  }

  public void setRefreshVisible(final boolean visible) {
    if (visible) {
      myRefreshIcon.resume();
    }
    else {
      myRefreshIcon.suspend();
    }

    myRefreshIcon.revalidate();
    myRefreshIcon.repaint();
  }

  public void setToolTipText(final String tooltip) {
    myRefreshIcon.setToolTipText(tooltip);
  }

  @NotNull
  public String ID() {
    return "RefreshIcon";
  }

  public Presentation getPresentation(@NotNull Type type) {
    return null;
  }

  public void install(@NotNull StatusBar statusBar) {
  }

  public void dispose() {
    setRefreshVisible(false);
  }
}
