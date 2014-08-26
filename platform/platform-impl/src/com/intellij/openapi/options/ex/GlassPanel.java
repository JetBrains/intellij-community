/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.Kernel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 10-Feb-2006
 */
public class GlassPanel extends JComponent {
  private final Set<JComponent> myLightComponents = new HashSet<JComponent>();
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
      int width = size.width - 1;
      int height = size.height - 1;

      Rectangle2D screen = new Rectangle2D.Double(0, 0, width, height);
      final Rectangle visibleRect = myPanel.getVisibleRect();
      final Point leftPoint = SwingUtilities.convertPoint(myPanel, new Point(visibleRect.x, visibleRect.y), surfaceComponent);
      Area innerPanel = new Area(new Rectangle2D.Double(leftPoint.x, leftPoint.y, visibleRect.width, visibleRect.height));
      Area mask = new Area(screen);
      ArrayList<JComponent> components = new ArrayList<JComponent>();
      for (JComponent lightComponent : myLightComponents) {
        final Area area = getComponentArea(surfaceComponent, lightComponent, 1);
        if (area == null) continue;
        components.add(lightComponent);

        if (lightComponent instanceof JLabel) {
          final JLabel label = (JLabel)lightComponent;
          final Component labelFor = label.getLabelFor();
          if (labelFor instanceof JComponent) {
            final Area labelForArea = getComponentArea(surfaceComponent, (JComponent)labelFor, 1);
            if (labelForArea != null) {
              components.add((JComponent)labelFor);
              area.add(labelForArea);
            }
          }
        }

        area.intersect(innerPanel);
        mask.subtract(area);
      }

      Graphics2D g2 = (Graphics2D)g;

      Color shieldColor = new Color(0.0f, 0.0f, 0.0f, 0.20f);
      Color boundsColor = Color.gray;
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(shieldColor);
      g2.fill(mask);

      g2.setColor(ColorUtil.toAlpha(Color.orange, 25));
      GraphicsConfig config = GraphicsUtil.setupAAPainting(g2);
      for (int i = 2; i > 0; i--) {
        g2.setStroke(new BasicStroke(i));
        Area arrr = new Area();
        for (JComponent component : components) {
          Area area = getComponentArea(surfaceComponent, component, i-1);
          if (area != null) {
            arrr.add(area);
          }
        }
        g2.draw(arrr);
      }

      config.restore();
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

    int hInset = isWithBorder ? 7 : isLabelFromTabbedPane ? 20 : 7;
    int vInset = isWithBorder ? 1 : isLabelFromTabbedPane ? 10 : 5;
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
  }

  public void clear() {
    myLightComponents.clear();
    setVisible(false);
  }
}
