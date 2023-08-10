// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.content;

import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.border.Border;
import java.awt.*;
import java.util.Objects;

public abstract class ContentLayout {
  ToolWindowContentUi ui;
  BaseLabel idLabel;

  ContentLayout(@NotNull ToolWindowContentUi ui) {
    this.ui = ui;
  }

  public abstract void init(@NotNull ContentManager contentManager);

  public abstract void reset();

  public abstract void layout();

  public void paintComponent(Graphics g) {
  }

  public abstract void update();

  public abstract void rebuild();

  public abstract int getMinimumWidth();

  public void contentAdded(@NotNull ContentManagerEvent event) {
  }

  public void contentRemoved(@NotNull ContentManagerEvent event) {
  }

  protected void updateIdLabel(BaseLabel label) {
    String title = ui.window.getStripeTitle();

    String suffix = getTitleSuffix();
    if (ExperimentalUI.isNewUI()) suffix = null;
    if (suffix != null) title += suffix;

    label.setText(title);
    Border border = JBUI.Borders.empty(0, 2, 0, 7);
    if (ExperimentalUI.isNewUI()) {
      border = shouldShowId()
               ? JBUI.Borders.empty(JBUI.CurrentTheme.ToolWindow.headerLabelLeftRightInsets())
               : JBUI.Borders.empty(JBUI.CurrentTheme.ToolWindow.headerTabLeftRightInsets());
    }
    Border oldBorder = label.getBorder();
    // Don't update component border (with following revalidation and repainting) if existing border is exactly the same we're going to set
    if (oldBorder == null || !Objects.equals(oldBorder.getClass(), border.getClass())
        || !oldBorder.getBorderInsets(label).equals(border.getBorderInsets(label))) {
      label.setBorder(border);
    }
    label.setVisible(shouldShowId());
  }

  private String getTitleSuffix() {
    ContentManager manager = ui.getContentManager();
    return switch (manager.getContentCount()) {
      case 0 -> null;
      case 1 -> {
        Content content = manager.getContent(0);
        if (content == null) yield null;
        final String text = content.getDisplayName();
        if (text != null && text.trim().length() > 0 && manager.canCloseContents()) {
          yield ":";
        }
        yield null;
      }
      default -> ":";
    };
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
    Component component =
      ComponentUtil.findParentByCondition(ui.getComponent(), c -> ClientProperty.get(c, ToolWindowContentUi.HIDE_ID_LABEL) != null);
    return component == null || !"true".equals(ClientProperty.get(component, ToolWindowContentUi.HIDE_ID_LABEL));
  }

  boolean isIdVisible() {
    return idLabel.isVisible();
  }
}
