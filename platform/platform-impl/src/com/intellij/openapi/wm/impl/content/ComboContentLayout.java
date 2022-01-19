// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.MouseDragHelper;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

final class ComboContentLayout extends ContentLayout {
  ContentComboLabel comboLabel;

  ComboContentLayout(ToolWindowContentUi ui) {
    super(ui);
  }

  @Override
  public void init(@NotNull ContentManager contentManager) {
    reset();

    idLabel = new BaseLabel(ui, Registry.is("ide.experimental.ui"));
    MouseDragHelper.setComponentDraggable(idLabel, true);
    comboLabel = new ContentComboLabel(this);
  }

  @Override
  public void reset() {
    idLabel = null;
    comboLabel = null;
  }

  @Override
  public void layout() {
    Rectangle bounds = ui.getTabComponent().getBounds();
    Dimension idSize = isIdVisible() ? idLabel.getPreferredSize() : JBUI.emptySize();

    int eachX = 0;
    int eachY = 0;

    idLabel.setBounds(eachX, eachY, idSize.width, bounds.height);
    eachX += idSize.width;

    Dimension comboSize = comboLabel.getPreferredSize();
    int spaceLeft = bounds.width - eachX - (isToDrawCombo() && isIdVisible() ? 3 : 0);

    int width = comboSize.width;
    if (width > spaceLeft) {
      width = spaceLeft;
    }

    comboLabel.setBounds(eachX, eachY, width, bounds.height);
  }

  @Override
  public int getMinimumWidth() {
    return idLabel == null ? 0 : idLabel.getPreferredSize().width;
  }

  @Override
  public void update() {
    updateIdLabel(idLabel);
    comboLabel.update();
  }

  @Override
  public void rebuild() {
    ui.getTabComponent().removeAll();

    ui.getTabComponent().add(idLabel);
    ToolWindowContentUi.initMouseListeners(idLabel, ui, true, true);

    ui.getTabComponent().add(comboLabel);
    ToolWindowContentUi.initMouseListeners(comboLabel, ui, false);
  }

  boolean isToDrawCombo() {
    return ui.getContentManager().getContentCount() > 1;
  }

  @Override
  public void showContentPopup(ListPopup listPopup) {
    final int width = comboLabel.getSize().width;
    listPopup.setMinimumSize(new Dimension(width, 0));
    listPopup.show(new RelativePoint(comboLabel, new Point(0, comboLabel.getHeight())));
  }

  @Override
  public @NlsActions.ActionText String getCloseActionName() {
    return IdeBundle.message("action.ComboContentLayout.close.view.text");
  }

  @Override
  public @NlsActions.ActionText String getCloseAllButThisActionName() {
    return IdeBundle.message("action.ComboContentLayout.close.other.views.text");
  }

  @Override
  public @NlsActions.ActionText String getPreviousContentActionName() {
    return IdeBundle.message("action.ComboContentLayout.select.previous.view.text");
  }

  @Override
  public @NlsActions.ActionText String getNextContentActionName() {
    return IdeBundle.message("action.ComboContentLayout.select.next.view.text");
  }
}
