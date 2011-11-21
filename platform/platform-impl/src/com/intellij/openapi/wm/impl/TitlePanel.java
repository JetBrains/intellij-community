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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 * @author max
 */
public class TitlePanel extends JPanel {
  private static final Color CNT_COLOR = new Gray(adjustBackgroundToSystem(194));
  private static final Color BND_COLOR = CNT_COLOR;

  public static final Color CNT_ACTIVE_COLOR = new Gray(adjustBackgroundToSystem(148));
  public static final Color BND_ACTIVE_COLOR = new Gray(adjustBackgroundToSystem(188));

  private static int adjustBackgroundToSystem(int mac_value) {
    if (SystemInfo.isMac) return mac_value;
    return mac_value * 941 / 784;
  }

  private boolean myActive = true;

  public static final int STRUT = 22;

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


    boolean active = isActive();

    Color bndColor = active ? BND_ACTIVE_COLOR : BND_COLOR;
    Color cntColor = active ? CNT_ACTIVE_COLOR : CNT_COLOR;

    g2d.setPaint(new GradientPaint(0, 0, bndColor, 0, getHeight(), cntColor));

    g2d.fillRect(0, 0, getWidth(), getHeight());

    g.setColor(Color.gray);
    g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
  }

  public void addButtons(final JComponent buttons) {
    final JPanel allButtons = new JPanel(new BorderLayout());
    allButtons.setOpaque(false);

    UIUtil.removeQuaquaVisualMarginsIn(buttons);

    allButtons.add(buttons, BorderLayout.CENTER);

    final NonOpaquePanel wrapper = new NonOpaquePanel(new BorderLayout());
    wrapper.add(Box.createVerticalStrut(STRUT), BorderLayout.WEST);
    wrapper.add(allButtons, BorderLayout.CENTER);

    add(wrapper, BorderLayout.EAST);
  }

  public boolean isActive() {
    return myActive;
  }
}
