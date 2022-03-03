// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content;

import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

abstract class ContentLayout {
  ToolWindowContentUi myUi;
  BaseLabel myIdLabel;

  ContentLayout(@NotNull ToolWindowContentUi ui) {
    myUi = ui;
  }

  public abstract void init(@NotNull ContentManager contentManager);

  public abstract void reset();

  public abstract void layout();

  public abstract void paintComponent(Graphics g);

  public abstract void update();

  public abstract void rebuild();

  public abstract int getMinimumWidth();

  public abstract void contentAdded(ContentManagerEvent event);

  public abstract void contentRemoved(ContentManagerEvent event);

  protected void updateIdLabel(BaseLabel label) {
    String title = myUi.window.getStripeTitle();

    String suffix = getTitleSuffix();
    if (ExperimentalUI.isNewToolWindowsStripes()) suffix = null;
    if (suffix != null) title += suffix;

    label.setText(title);
    label.setBorder(JBUI.Borders.empty(0, 2, 0, 7));
    if (ExperimentalUI.isNewToolWindowsStripes()) {
      label.setBorder(shouldShowId()
                      ? JBUI.Borders.empty(JBUI.CurrentTheme.ToolWindow.headerLabelLeftRightInsets())
                      : JBUI.Borders.empty(JBUI.CurrentTheme.ToolWindow.headerTabLeftRightInsets()));
    }
    label.setVisible(shouldShowId());
  }

  private String getTitleSuffix() {
    ContentManager manager = myUi.getContentManager();
    switch (manager.getContentCount()) {
      case 0:
        return null;
      case 1:
        Content content = manager.getContent(0);
        if (content == null) return null;

        final String text = content.getDisplayName();
        if (text != null && text.trim().length() > 0 && manager.canCloseContents()) {
          return ":";
        }
        return null;
      default:
        return ":";
    }
  }

  public abstract void showContentPopup(ListPopup listPopup);

  @ActionText
  public abstract String getCloseActionName();

  @ActionText
  public abstract String getCloseAllButThisActionName();

  @ActionText
  public abstract String getPreviousContentActionName();

  @ActionText
  public abstract String getNextContentActionName();

  protected boolean shouldShowId() {
    return !"true".equals(UIUtil.getClientProperty(
      ComponentUtil.findParentByCondition(myUi.getComponent(), c -> UIUtil.getClientProperty(c, ToolWindowContentUi.HIDE_ID_LABEL) != null),
      ToolWindowContentUi.HIDE_ID_LABEL));
  }

  boolean isIdVisible() {
    return myIdLabel.isVisible();
  }
}
