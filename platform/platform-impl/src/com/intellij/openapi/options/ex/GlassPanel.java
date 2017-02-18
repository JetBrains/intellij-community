/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.openapi.options.ex;

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.Kernel;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 10-Feb-2006
 */
public class GlassPanel extends JComponent {
  private final Set<JComponent> myLightComponents = new HashSet<>();
  private final JComponent myPanel;
  private static final Insets EMPTY_INSETS = new Insets(0, 0, 0, 0);


  public GlassPanel(JComponent containingPanel) {
    myPanel = containingPanel;
    setVisible(false);
  }

  public void paintComponent(Graphics g) {
    paintSpotlights(g);
  }

  protected void paintSpotlights(Graphics g) {
    paintSpotlight(g, this);
  }

  public void paintSpotlight(final Graphics g, final JComponent surfaceComponent) {
    Dimension size = surfaceComponent.getSize();
    if (myLightComponents.size() > 0) {
      int stroke = 2;

      final Rectangle visibleRect = myPanel.getVisibleRect();
      final Point leftPoint = SwingUtilities.convertPoint(myPanel, new Point(visibleRect.x, visibleRect.y), surfaceComponent);
      Area innerPanel = new Area(new Rectangle2D.Double(leftPoint.x, leftPoint.y, visibleRect.width, visibleRect.height));
      Area mask = new Area(new Rectangle(-stroke, -stroke, 2 * stroke + size.width, 2 * stroke + size.height));
      for (JComponent lightComponent : myLightComponents) {
        final Area area = getComponentArea(surfaceComponent, lightComponent, 1);
        if (area == null) continue;

        if (lightComponent instanceof JLabel) {
          final JLabel label = (JLabel)lightComponent;
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

        Color background = surfaceComponent.getBackground();
        g2.setColor(ColorUtil.toAlpha(background == null ? null : background.darker(), 100));
        g2.fill(mask);

        g2.setStroke(new BasicStroke(stroke));
        g2.setColor(ColorUtil.toAlpha(JBColor.ORANGE, 100));
        g2.draw(mask);
      }
      finally {
        clip.dispose();
      }
    }
  }

  @Nullable
  private Area getComponentArea(final JComponent surfaceComponent, final JComponent lightComponent, int offset) {
    if (!lightComponent.isShowing()) return null;

    final Point panelPoint = SwingUtilities.convertPoint(lightComponent, new Point(0, 0), surfaceComponent);
    final int x = panelPoint.x;
    final int y = panelPoint.y;

    Insets insetsToIgnore = lightComponent.getInsets();
    final boolean isWithBorder = Boolean.TRUE.equals(lightComponent.getClientProperty(SearchUtil.HIGHLIGHT_WITH_BORDER));
    final boolean isLabelFromTabbedPane = Boolean.TRUE.equals(lightComponent.getClientProperty(JBTabbedPane.LABEL_FROM_TABBED_PANE));

    if ((insetsToIgnore == null || (UIUtil.isUnderAquaLookAndFeel() && lightComponent instanceof JButton)) || isWithBorder) {
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

  protected int getComponentHInset(boolean isWithBorder, boolean isLabelFromTabbedPane) {
    return isWithBorder ? 7 : isLabelFromTabbedPane ? 20 : 7;
  }

  protected int getComponentVInset(boolean isWithBorder, boolean isLabelFromTabbedPane) {
    return isWithBorder ? 1 : isLabelFromTabbedPane ? 10 : 5;
  }

  protected static Kernel getBlurKernel(int blurSize) {
    if (blurSize <= 0) return null;

    int size = blurSize * blurSize;
    float coeff = 1.0f / size;
    float[] kernelData = new float[size];

    for (int i = 0; i < size; i ++) {
      kernelData[i] = coeff;
    }

    return new Kernel(blurSize, blurSize, kernelData);
  }


  public static double getArea(JComponent component) {
    return Math.PI * component.getWidth() * component.getHeight() / 4.0;
  }

  public void addSpotlight(final JComponent component) {
    myLightComponents.add(component);
    setVisible(true);
  }

  public void removeSpotlight(final JComponent component){
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
