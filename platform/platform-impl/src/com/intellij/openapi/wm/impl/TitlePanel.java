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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.SameColor;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class TitlePanel extends JPanel {
  private static final Color CNT_COLOR = new SameColor(184);
  private static final Color BND_COLOR = CNT_COLOR;

  public static final Color CNT_ACTIVE_COLOR = new Color(105, 128, 180);
  public static final Color BND_ACTIVE_COLOR = CNT_ACTIVE_COLOR.brighter();

  /* Gray-ish colors
  public static final Color CNT_ACTIVE_COLOR = new SameColor(120);
  public static final Color BND_ACTIVE_COLOR = new SameColor(160);
  */

  private boolean myActive = true;
  private JComponent mySideButtons;

  private final Icon mySeparatorActive = IconLoader.getIcon("/general/separator.png");
  private final Icon mySeparatorInactive = IconLoader.getIcon("/general/inactiveSeparator.png");

  public final static Color ACTIVE_SIDE_BUTTON_BG = new Color(179, 197, 231);
  public final static Color INACTIVE_SIDE_BUTTON_BG = new Color(200, 200, 200);
  public static final int STRUT = 0;

  TitlePanel() {
    super(new BorderLayout());
  }


  public void addTitle(JComponent component) {
    add(component, BorderLayout.CENTER);
  }

  public final void setActive(final boolean active) {
    if (active == myActive) {
      return;
    }

    myActive = active;
    repaint();
  }

  @Override
  protected void addImpl(Component comp, Object constraints, int index) {
    UIUtil.removeQuaquaVisualMarginsIn(comp);
    super.addImpl(comp, constraints, index);
  }

  protected final void paintComponent(final Graphics g) {
    super.paintComponent(g);

    final Graphics2D g2d = (Graphics2D) g;


    Color bndColor = myActive ? BND_ACTIVE_COLOR : BND_COLOR;
    Color cntColor = myActive ? CNT_ACTIVE_COLOR : CNT_COLOR;

    g2d.setPaint(new GradientPaint(0, STRUT, bndColor, 0, getHeight(), cntColor));
    if (mySideButtons.isVisible()) {
      final Rectangle sideRec = SwingUtilities.convertRectangle(mySideButtons.getParent(), mySideButtons.getBounds(), this);
      g2d.fillRect(0, STRUT, getWidth() - sideRec.width, getHeight());

      g2d.setColor(UIUtil.getHeaderInactiveColor());
      final Color buttonInnerColor = myActive ? ACTIVE_SIDE_BUTTON_BG : INACTIVE_SIDE_BUTTON_BG;
      g2d.setPaint(new GradientPaint(sideRec.x, sideRec.y, Color.white, sideRec.x, (int)sideRec.getMaxY() - 1, buttonInnerColor));
      g2d.fillRect(sideRec.x + 2, sideRec.y, sideRec.width - 2, sideRec.height);


      Icon separator = myActive ? mySeparatorActive : mySeparatorInactive;
      separator.paintIcon(this, g, sideRec.x, sideRec.y);

    } else {
      g2d.fillRect(0, STRUT, getWidth(), getHeight());
    }
  }

  public void addButtons(final JComponent buttons, JComponent sideButtons) {
    mySideButtons = sideButtons;
    mySideButtons.setBorder(new EmptyBorder(0, 6, 0, 6));

    final JPanel allButtons = new JPanel(new BorderLayout());
    allButtons.setOpaque(false);

    UIUtil.removeQuaquaVisualMarginsIn(buttons);
    UIUtil.removeQuaquaVisualMarginsIn(sideButtons);

    allButtons.add(buttons, BorderLayout.CENTER);
    allButtons.add(mySideButtons, BorderLayout.EAST);

    final NonOpaquePanel wrapper = new NonOpaquePanel(new BorderLayout());
    wrapper.add(Box.createVerticalStrut(STRUT), BorderLayout.NORTH);
    wrapper.add(allButtons, BorderLayout.CENTER);

    add(wrapper, BorderLayout.EAST);
  }
}
