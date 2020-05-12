// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsActions;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

final class ComboContentLayout extends ContentLayout {
  ContentComboLabel myComboLabel;

  ComboContentLayout(ToolWindowContentUi ui) {
    super(ui);
  }

  @Override
  public void init(@NotNull ContentManager contentManager) {
    reset();

    myIdLabel = new BaseLabel(myUi, false);
    myComboLabel = new ContentComboLabel(this);
  }

  @Override
  public void reset() {
    myIdLabel = null;
    myComboLabel = null;
  }

  @Override
  public void layout() {
    Rectangle bounds = myUi.getTabComponent().getBounds();
    Dimension idSize = isIdVisible() ? myIdLabel.getPreferredSize() : JBUI.emptySize();

    int eachX = 0;
    int eachY = 0;

    myIdLabel.setBounds(eachX, eachY, idSize.width, bounds.height);
    eachX += idSize.width;

    Dimension comboSize = myComboLabel.getPreferredSize();
    int spaceLeft = bounds.width - eachX - (isToDrawCombo() && isIdVisible() ? 3 : 0);

    int width = comboSize.width;
    if (width > spaceLeft) {
      width = spaceLeft;
    }

    myComboLabel.setBounds(eachX, eachY, width, bounds.height);
  }

  @Override
  public int getMinimumWidth() {
    return myIdLabel != null ? myIdLabel.getPreferredSize().width : 0;
  }

  @Override
  public void paintComponent(Graphics g) {
    if (!isToDrawCombo() || !myIdLabel.isVisible()) return;

    Rectangle r = myIdLabel.getBounds();
    g.setColor(ColorUtil.toAlpha(UIUtil.getLabelForeground(), 20));
    g.drawLine(r.width, 0, r.width, r.height);
    g.setColor(UIUtil.CONTRAST_BORDER_COLOR);
    g.drawLine(r.width - 1, 0, r.width - 1, r.height);
  }

  @Override
  public void update() {
    updateIdLabel(myIdLabel);
    myComboLabel.update();
  }

  @Override
  public void rebuild() {
    myUi.getTabComponent().removeAll();

    myUi.getTabComponent().add(myIdLabel);
    ToolWindowContentUi.initMouseListeners(myIdLabel, myUi, true);

    myUi.getTabComponent().add(myComboLabel);
    ToolWindowContentUi.initMouseListeners(myComboLabel, myUi, false);
  }

  boolean isToDrawCombo() {
    ContentManager manager = myUi.getContentManager();
    return manager != null && manager.getContentCount() > 1;
  }

  @Override
  public void contentAdded(ContentManagerEvent event) {
  }

  @Override
  public void contentRemoved(ContentManagerEvent event) {
  }

  @Override
  public void showContentPopup(ListPopup listPopup) {
    final int width = myComboLabel.getSize().width;
    listPopup.setMinimumSize(new Dimension(width, 0));
    listPopup.show(new RelativePoint(myComboLabel, new Point(0, myComboLabel.getHeight())));
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
