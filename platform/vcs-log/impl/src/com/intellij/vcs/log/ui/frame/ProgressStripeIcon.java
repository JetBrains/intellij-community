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

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

public abstract class ProgressStripeIcon implements Icon {
  private static final int TRANSLATE = 1;
  private static final int HEIGHT = 3;
  @NotNull
  private final JComponent myReferenceComponent;
  private final int myShift;

  private ProgressStripeIcon(@NotNull JComponent component, int shift) {
    myReferenceComponent = component;
    myShift = shift;
  }

  public abstract int getChunkWidth();

  protected abstract void paint(@NotNull Graphics2D g2, int x, int y, int shift);

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    Graphics2D g2 = (Graphics2D)g;

    int shift = myShift - getChunkWidth();
    while (shift < getIconWidth()) {
      paint(g2, x, y, shift);
      shift += getChunkWidth();
    }

    config.restore();
  }

  @Override
  public int getIconWidth() {
    return myReferenceComponent.getWidth();
  }

  @Override
  public int getIconHeight() {
    return getHeight();
  }

  public static int getHeight() {
    return JBUI.scale(HEIGHT);
  }

  private static class GradientIcon extends ProgressStripeIcon {
    @SuppressWarnings("UseJBColor")
    private static final Color DARK = new ProgressStripeColor(new JBColor(Gray._165, new Color(0x6a6a6a)),
                                                              new Color(0x4d9ff8));
    @SuppressWarnings("UseJBColor")
    private static final Color LIGHT = new ProgressStripeColor(new JBColor(new Color(0xdbdbdb), new Color(0x838383)),
                                                               new Color(0x90c2f8));
    private static final int GRADIENT = 128;
    private static final int GRADIENT_HEIGHT = 2;

    private GradientIcon(@NotNull JComponent component, int shift) {
      super(component, shift);
    }

    @Override
    public int getChunkWidth() {
      return 2 * JBUI.scale(GRADIENT);
    }

    @Override
    public void paint(@NotNull Graphics2D g2, int x, int y, int shift) {
      Color dark = DARK;
      Color light = LIGHT;
      g2.setPaint(new GradientPaint(x + shift, y, dark, x + shift + JBUI.scale(GRADIENT), y, light));
      g2.fill(new Rectangle(x + shift, y, JBUI.scale(GRADIENT), getIconHeight()));
      g2.setPaint(new GradientPaint(x + shift + JBUI.scale(GRADIENT), y, light, x + shift + 2 * JBUI.scale(GRADIENT), y, dark));
      g2.fill(new Rectangle(x + shift + JBUI.scale(GRADIENT), y, JBUI.scale(GRADIENT), getIconHeight()));
    }

    @Override
    public int getIconHeight() {
      return JBUI.scale(GRADIENT_HEIGHT);
    }

    private static class ProgressStripeColor extends JBColor {
      private ProgressStripeColor(@NotNull JBColor defaultColor, @NotNull Color blueColor) {
        super(() -> {
          if (UIUtil.isUnderAquaBasedLookAndFeel() && !UIUtil.isUnderDarcula() && !UIUtil.isGraphite()) {
            return blueColor;
          }
          return defaultColor;
        });
      }
    }
  }

  @NotNull
  public static AsyncProcessIcon generateIcon(@NotNull JComponent component) {
    List<Icon> result = ContainerUtil.newArrayList();
    for (int i = 0; i < 2 * JBUI.scale(GradientIcon.GRADIENT); i += JBUI.scale(TRANSLATE)) {
      result.add(new GradientIcon(component, i));
    }

    Icon passive = result.get(0);
    AsyncProcessIcon icon = new AsyncProcessIcon("ProgressStripeIcon", result.toArray(new Icon[0]), passive) {
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
}
