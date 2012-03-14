/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

      for (JComponent lightComponent : myLightComponents) {
        final Area area = getComponentArea(surfaceComponent, lightComponent);
        if (area == null) continue;

        if (lightComponent instanceof JLabel) {
          final JLabel label = (JLabel)lightComponent;
          final Component labelFor = label.getLabelFor();
          if (labelFor instanceof JComponent) {
            final Area labelForArea = getComponentArea(surfaceComponent, (JComponent)labelFor);
            if (labelForArea != null) {
              area.add(labelForArea);
            }
          }
        }

        area.intersect(innerPanel);
        mask.subtract(area);
      }

      Graphics2D g2 = (Graphics2D)g;

      Color shieldColor = new Color(0.0f, 0.0f, 0.0f, 0.15f);
      Color boundsColor = Color.gray;
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(shieldColor);
      g2.fill(mask);

      g2.setColor(boundsColor);
      g2.draw(mask);
    }
  }

  @Nullable
  private Area getComponentArea(final JComponent surfaceComponent, final JComponent lightComponent) {
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
    final Area area = new Area(new RoundRectangle2D.Double(x - hInset + insetsToIgnore.left,
                                                           y - vInset + insetsToIgnore.top,
                                                           lightComponent.getWidth() + hInset * 2 - insetsToIgnore.right - insetsToIgnore.left,
                                                           lightComponent.getHeight() + vInset * 2 - insetsToIgnore.top - insetsToIgnore.bottom,
                                                           6, 6));
    return area;
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
