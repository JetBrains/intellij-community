// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public abstract class ProgressStripeIcon implements Icon {
  private static final int TRANSLATE = 1;
  private final @NotNull JComponent myReferenceComponent;
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

  private static final class GradientIcon extends ProgressStripeIcon {
    private static final int GRADIENT = 128;
    private static final int GRADIENT_HEIGHT = 3;

    private GradientIcon(@NotNull JComponent component, int shift) {
      super(component, shift);
    }

    @Override
    public int getChunkWidth() {
      return 2 * JBUIScale.scale(GRADIENT);
    }

    @Override
    public void paint(@NotNull Graphics2D g2, int x, int y, int shift) {
      // See DarculaProgressBarUI and MacIntelliJProgressBarUI
      Color dark = UIUtil.isGraphite() ? Gray.xD4 : JBUI.CurrentTheme.ProgressBar.INDETERMINATE_START;
      //noinspection UseJBColor
      Color light = UIUtil.isGraphite() ? new Color(0x989a9e) : JBUI.CurrentTheme.ProgressBar.INDETERMINATE_END;

      g2.setPaint(new GradientPaint(x + shift, y, dark, x + shift + JBUIScale.scale(GRADIENT), y, light));
      g2.fill(new Rectangle(x + shift, y, JBUIScale.scale(GRADIENT), getIconHeight()));
      g2.setPaint(new GradientPaint(x + shift + JBUIScale.scale(GRADIENT), y, light, x + shift + 2 * JBUIScale.scale(GRADIENT), y, dark));
      g2.fill(new Rectangle(x + shift + JBUIScale.scale(GRADIENT), y, JBUIScale.scale(GRADIENT), getIconHeight()));
    }

    @Override
    public int getIconHeight() {
      return JBUIScale.scale(GRADIENT_HEIGHT);
    }
  }

  public static @NotNull AsyncProcessIcon generateIcon(@NotNull JComponent component) {
    List<Icon> result = new ArrayList<>();
    for (int i = 0; i < 2 * JBUIScale.scale(GradientIcon.GRADIENT); i += JBUIScale.scale(TRANSLATE)) {
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
