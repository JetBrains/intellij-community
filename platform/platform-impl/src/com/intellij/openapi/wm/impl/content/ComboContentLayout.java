/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.content;

import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.impl.TitlePanel;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;

import java.awt.*;
import java.awt.geom.GeneralPath;

class ComboContentLayout extends ContentLayout {

  BaseLabel myIdLabel;
  ContentComboLabel myComboLabel;

  ComboContentLayout(ToolWindowContentUi ui) {
    super(ui);
  }

  @Override
  public void init() {
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
    Rectangle bounds = myUi.getBounds();
    Dimension idSize = myIdLabel.getPreferredSize();

    int eachX = 0;
    int eachY = TitlePanel.STRUT;

    myIdLabel.setBounds(eachX, eachY, idSize.width, bounds.height);
    eachX += idSize.width;

    Dimension comboSize = myComboLabel.getPreferredSize();
    int spaceLeft = bounds.width - eachX - (isToDrawCombo() ? 3 : 0);

    int width = comboSize.width;
    if (width > spaceLeft) {
      width = spaceLeft;
    }

    myComboLabel.setBounds(eachX, eachY, width, bounds.height);
  }

  @Override
  public void paintComponent(Graphics g) {
    if (!isToDrawCombo()) return;

    final Graphics2D g2d = (Graphics2D)g;

    final GraphicsConfig c = new GraphicsConfig(g);
    c.setAntialiasing(true);

    fillTabShape(g2d, myComboLabel, getShapeFor(myComboLabel), true);

    c.restore();
  }

  @Override
  public void paintChildren(Graphics g) {
    if (!isToDrawCombo()) return;

    final GraphicsConfig c = new GraphicsConfig(g);
    c.setAntialiasing(true);

    final Graphics2D g2d = (Graphics2D)g;

    final Color edges = myUi.myWindow.isActive() ? TAB_BORDER_ACTIVE_WINDOW : TAB_BORDER_PASSIVE_WINDOW;
    g2d.setColor(edges);

    Shape shape = getShapeFor(myComboLabel);
    g2d.draw(shape);

    c.restore();
  }

  private Shape getShapeFor(ContentComboLabel label) {
    final Rectangle bounds = label.getBounds();

    if (bounds.width <= 0 || bounds.height <= 0) return new GeneralPath();

    bounds.y = bounds.y - TitlePanel.STRUT;
    int height = bounds.height - 1;

    bounds.width += 1;

    int arc = TAB_ARC;

    final GeneralPath path = new GeneralPath();
    path.moveTo(bounds.x, bounds.y + height - arc);
    path.lineTo(bounds.x, bounds.y + arc);
    path.quadTo(bounds.x, bounds.y, bounds.x + arc, bounds.y);
    path.lineTo(bounds.x + bounds.width - arc, bounds.y);
    path.quadTo(bounds.x + bounds.width, bounds.y, bounds.x + bounds.width, bounds.y + arc);
    path.lineTo(bounds.x + bounds.width, bounds.y + height - arc);
    path.quadTo(bounds.x + bounds.width, bounds.y + height, bounds.x + bounds.width - arc, bounds.y + height);
    path.lineTo(bounds.x + arc, bounds.y + height);
    path.quadTo(bounds.x, bounds.y + height, bounds.x, bounds.y + height - arc);
    path.closePath();

    return path;
  }


  @Override
  public void update() {
    updateIdLabel(myIdLabel);
    myComboLabel.update();
  }

  @Override
  public void rebuild() {
    myUi.removeAll();

    myUi.add(myIdLabel);
    myUi.initMouseListeners(myIdLabel, myUi);

    myUi.add(myComboLabel);
    myUi.initMouseListeners(myComboLabel, myUi);
  }

  boolean isToDrawCombo() {
    return myUi.myManager.getContentCount() > 1;
  }

  @Override
  public void contentAdded(ContentManagerEvent event) {
  }

  @Override
  public void contentRemoved(ContentManagerEvent event) {
  }

  @Override
  public void showContentPopup(ListPopup listPopup) {
    listPopup.setMinimumSize(new Dimension(myComboLabel.getPreferredSize().width, 0));
    listPopup.showUnderneathOf(myComboLabel);
  }

  @Override
  public RelativeRectangle getRectangleFor(Content content) {
    return null;
  }

  @Override
  public Component getComponentFor(Content content) {
    return null;
  }
}
