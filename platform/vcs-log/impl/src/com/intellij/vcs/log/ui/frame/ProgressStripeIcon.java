/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.frame;

import com.intellij.ui.JBColor;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

public class ProgressStripeIcon {
  private static final int TRANSLATE = 1;
  private static final Icon STRIPES = VcsLogIcons.Stripes;

  private static class StripesIcon implements Icon {
    @NotNull
    private final JComponent myReferenceComponent;
    private final int myShift;
    @NotNull
    private final Icon myCropIcon;
    private final Icon myIcon;

    public StripesIcon(@NotNull JComponent component, int shift, @NotNull Icon icon) {
      myReferenceComponent = component;
      myShift = shift;

      myIcon = icon;
      myCropIcon = IconUtil.cropIcon(icon, new Rectangle(myIcon.getIconWidth() - myShift, 0, myShift, icon.getIconHeight()));
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      myCropIcon.paintIcon(c, g, x, y);
      int shift = myShift;
      while (shift < getIconWidth()) {
        myIcon.paintIcon(c, g, x + shift, y);
        shift += myIcon.getIconWidth();
      }
    }

    @Override
    public int getIconWidth() {
      return myReferenceComponent.getWidth();
    }

    @Override
    public int getIconHeight() {
      return myIcon.getIconHeight();
    }
  }

  private static class GradientIcon implements Icon {
    private static final JBColor DARK_BLUE = new JBColor(0x4d9ff8, 0x525659);
    private static final JBColor LIGHT_BLUE = new JBColor(0x90c2f8, 0x5e6266);
    static final int GRADIENT_WIDTH = JBUI.scale(128);
    @NotNull
    private final JComponent myReferenceComponent;
    private final int myShift;

    private GradientIcon(@NotNull JComponent component, int shift) {
      myReferenceComponent = component;
      myShift = shift;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D)g;
      int shift = myShift - 2 * GRADIENT_WIDTH;
      while (shift < getIconWidth()) {
        paint(g2, x, y, shift);
        shift += 2 * GRADIENT_WIDTH;
      }
    }

    public void paint(Graphics2D g2, int x, int y, int shift) {
      g2.setPaint(new GradientPaint(x + shift, y, DARK_BLUE, x + shift + GRADIENT_WIDTH, y, LIGHT_BLUE));
      g2.fill(new Rectangle(x + shift, y, GRADIENT_WIDTH, getIconHeight()));
      g2.setPaint(new GradientPaint(x + shift + GRADIENT_WIDTH, y, LIGHT_BLUE, x + shift + 2 * GRADIENT_WIDTH, y, DARK_BLUE));
      g2.fill(new Rectangle(x + shift + GRADIENT_WIDTH, y, GRADIENT_WIDTH, getIconHeight()));
    }

    @Override
    public int getIconWidth() {
      return myReferenceComponent.getWidth();
    }

    @Override
    public int getIconHeight() {
      return STRIPES.getIconHeight();
    }
  }

  @NotNull
  public static AsyncProcessIcon generateIcon(@NotNull JComponent component) {
    List<Icon> result = ContainerUtil.newArrayList();
    if (UIUtil.isUnderAquaBasedLookAndFeel() && !UIUtil.isUnderDarcula()) {
      for (int i = 0; i < 2 * GradientIcon.GRADIENT_WIDTH; i += JBUI.scale(TRANSLATE)) {
        result.add(new GradientIcon(component, i));
      }
    }
    else {
      for (int i = 0; i < STRIPES.getIconWidth(); i += JBUI.scale(TRANSLATE)) {
        result.add(new StripesIcon(component, i, STRIPES));
      }
      result = ContainerUtil.reverse(result);
    }

    Icon passive = result.get(0);
    AsyncProcessIcon icon = new AsyncProcessIcon("ProgressWithStripes", result.toArray(new Icon[result.size()]), passive) {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(component.getWidth(), passive.getIconHeight());
      }
    };
    component.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        super.componentResized(e);
        icon.revalidate();
      }
    });
    return icon;
  }

  public static int getHeight() {
    return STRIPES.getIconHeight();
  }
}
