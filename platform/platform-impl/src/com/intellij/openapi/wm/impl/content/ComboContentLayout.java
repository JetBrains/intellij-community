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
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;

import java.awt.*;
import java.awt.image.BufferedImage;

class ComboContentLayout extends ContentLayout {

  BaseLabel myIdLabel;
  ContentComboLabel myComboLabel;
  private BufferedImage myImage;

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
    myImage = null;
  }

  @Override
  public void layout() {
    Rectangle bounds = myUi.getBounds();
    Dimension idSize = myIdLabel.getPreferredSize();

    int eachX = 0;
    int eachY = 0;

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

    Rectangle r = myComboLabel.getBounds();
    
    if (myImage == null || myImage.getHeight() != r.height || myImage.getWidth() != r.width) {
      myImage = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
      final Graphics2D g2d = myImage.createGraphics();
      final GraphicsConfig c = new GraphicsConfig(g);
      c.setAntialiasing(true);
  
      g2d.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 10), 0, r.height, new Color(0, 0, 0, 30)));
      g2d.fillRect(0, 0, r.width, r.height);
  
      g2d.setColor(new Color(0, 0, 0, 60));
      g2d.drawLine(0, 0, 0, r.height);
      g2d.drawLine(r.width - 1, 0, r.width - 1, r.height);
      
      g2d.setColor(new Color(255, 255, 255, 80));
      g2d.drawRect(1, 0, r.width - 3, r.height - 1);
      
      g2d.dispose();
    }
    
    g.drawImage(myImage, r.x, r.y, null);
  }

  @Override
  public void paintChildren(Graphics g) {
    if (!isToDrawCombo()) return;

    final GraphicsConfig c = new GraphicsConfig(g);
    c.setAntialiasing(true);

    final Graphics2D g2d = (Graphics2D)g;
    c.restore();
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
  public boolean shouldDrawDecorations() {
    return isToDrawCombo();
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
