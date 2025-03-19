// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.options.ex;

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTabbedPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashSet;
import java.util.Set;

public final class GlassPanel extends JComponent {
  private final Set<JComponent> myLightComponents = new HashSet<>();
  private final JComponent myPanel;
  private static final Insets EMPTY_INSETS = new Insets(0, 0, 0, 0);
  private static final String SPOTLIGHT_BACKGROUND_COLOR_KEY = "Settings.Spotlight.backgroundColor";
  private static final String SPOTLIGHT_BORDER_COLOR_KEY = "Settings.Spotlight.borderColor";
  private static final JBColor FALLBACK_SPOTLIGHT_BORDER_COLOR = new JBColor(
    JBColor.namedColor("ColorPalette.Orange6", 0xE08855),
    JBColor.namedColor("ColorPalette.Orange4", 0xA36B4E)
  );

  public GlassPanel(JComponent containingPanel) {
    myPanel = containingPanel;
    setVisible(false);
  }

  @Override
  public void paintComponent(Graphics g) {
    paintSpotlights(g);
  }

  private void paintSpotlights(Graphics g) {
    paintSpotlight(g, this);
  }

  public void paintSpotlight(final Graphics g, final JComponent surfaceComponent) {
    Dimension size = surfaceComponent.getSize();
    if (!myLightComponents.isEmpty()) {
      int stroke = 2;

      final Rectangle visibleRect = myPanel.getVisibleRect();
      final Point leftPoint = SwingUtilities.convertPoint(myPanel, new Point(visibleRect.x, visibleRect.y), surfaceComponent);
      Area innerPanel = new Area(new Rectangle2D.Double(leftPoint.x, leftPoint.y, visibleRect.width, visibleRect.height));
      Area mask = new Area(new Rectangle(-stroke, -stroke, 2 * stroke + size.width, 2 * stroke + size.height));
      for (JComponent lightComponent : myLightComponents) {
        final Area area = getComponentArea(surfaceComponent, lightComponent, 1);
        if (area == null) continue;

        if (lightComponent instanceof JLabel label) {
          final Component labelFor = label.getLabelFor();
          if (labelFor instanceof JComponent) {
            final Area labelForArea = getComponentArea(surfaceComponent, (JComponent)labelFor, 1);
            if (labelForArea != null) {
              area.add(labelForArea);
            }
          }
        }

        area.intersect(innerPanel);
        mask.subtract(area);
      }
      Graphics clip = g.create(0, 0, size.width, size.height);
      try {
        Graphics2D g2 = (Graphics2D)clip;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        final Color background = getOverlayColor(surfaceComponent);
        g2.setColor(background);
        g2.fill(mask);

        g2.setStroke(new BasicStroke(stroke));
        final Color borderColor = UIManager.getColor(SPOTLIGHT_BORDER_COLOR_KEY);
        g2.setColor(borderColor != null ? borderColor : FALLBACK_SPOTLIGHT_BORDER_COLOR);
        g2.draw(mask);
      }
      finally {
        clip.dispose();
      }
    }
  }

  private static @Nullable Area getComponentArea(final JComponent surfaceComponent, final JComponent lightComponent, int offset) {
    if (!lightComponent.isShowing()) return null;

    final Point panelPoint = SwingUtilities.convertPoint(lightComponent, new Point(0, 0), surfaceComponent);
    final int x = panelPoint.x;
    final int y = panelPoint.y;

    Insets insetsToIgnore = lightComponent.getInsets();
    final boolean isWithBorder = Boolean.TRUE.equals(lightComponent.getClientProperty(SearchUtil.HIGHLIGHT_WITH_BORDER));
    final boolean isLabelFromTabbedPane = Boolean.TRUE.equals(lightComponent.getClientProperty(JBTabbedPane.LABEL_FROM_TABBED_PANE));

    if (insetsToIgnore == null || isWithBorder) {
      insetsToIgnore = EMPTY_INSETS;
    }

    int hInset = getComponentHInset(isWithBorder, isLabelFromTabbedPane);
    int vInset = getComponentVInset(isWithBorder, isLabelFromTabbedPane);
    hInset += offset;
    vInset += offset;
    int xCoord = x - hInset + insetsToIgnore.left;
    int yCoord = y - vInset + insetsToIgnore.top;
    int width = lightComponent.getWidth() + hInset * 2 - insetsToIgnore.right - insetsToIgnore.left;
    int height = lightComponent.getHeight() + vInset * 2 - insetsToIgnore.top - insetsToIgnore.bottom;
    return new Area(new RoundRectangle2D.Double(xCoord,
                                                yCoord,
                                                width,
                                                height,
                                                Math.min(height, 30), Math.min(height, 30)));
  }

  private static int getComponentHInset(boolean isWithBorder, boolean isLabelFromTabbedPane) {
    return isWithBorder ? 7 : isLabelFromTabbedPane ? 20 : 7;
  }

  private static int getComponentVInset(boolean isWithBorder, boolean isLabelFromTabbedPane) {
    return isWithBorder ? 1 : isLabelFromTabbedPane ? 10 : 5;
  }

  public static double getArea(JComponent component) {
    return Math.PI * component.getWidth() * component.getHeight() / 4.0;
  }

  private static @NotNull Color getOverlayColor(JComponent surfaceComponent) {
    final Color background = UIManager.getColor(SPOTLIGHT_BACKGROUND_COLOR_KEY);
    if (background != null) return background;

    final Color surfaceComponentBackground = surfaceComponent.getBackground();
    final Color fallbackBackground =
      ColorUtil.toAlpha(surfaceComponentBackground == null ? null : surfaceComponentBackground.darker(), 100);

    return fallbackBackground;
  }

  public void addSpotlight(final JComponent component) {
    myLightComponents.add(component);
    setVisible(true);
  }

  public void removeSpotlight(final JComponent component) {
    myLightComponents.remove(component);
    if (myLightComponents.isEmpty()) {
      setVisible(false);
    }
  }

  public void clear() {
    myLightComponents.clear();
    setVisible(false);
  }
}
