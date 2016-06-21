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
package com.intellij.vcs.log.ui.render;
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

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TagLabelPainter {
  public static final int TOP_TEXT_PADDING = JBUI.scale(2);
  public static final int BOTTOM_TEXT_PADDING = JBUI.scale(1);
  public static final int GRADIENT_WIDTH = JBUI.scale(50);
  public static final int RIGHT_PADDING = JBUI.scale(5);
  public static final int MIDDLE_PADDING = JBUI.scale(5);

  @NotNull
  private List<Pair<String, TagIcon>> myLabels = ContainerUtil.newArrayList();
  private int myHeight = JBUI.scale(22);
  private int myWidth = 0;
  @NotNull
  private Color myBackground = UIUtil.getTableBackground();
  @NotNull
  private Color myForeground = UIUtil.getTableForeground();

  public void customizePainter(@NotNull JComponent component,
                               @NotNull Collection<VcsRef> references,
                               @NotNull Color background,
                               @NotNull Color foreground) {
    myBackground = background;
    myForeground = foreground;

    FontMetrics metrics = component.getFontMetrics(getReferenceFont());
    myHeight = metrics.getHeight() + TOP_TEXT_PADDING + BOTTOM_TEXT_PADDING;
    myWidth = GRADIENT_WIDTH + RIGHT_PADDING;

    myLabels = ContainerUtil.newArrayList();
    for (Map.Entry<VcsRefType, Collection<VcsRef>> typeAndRefs : ContainerUtil.groupBy(references, VcsRef::getType).entrySet()) {
      VcsRef firstRef = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(typeAndRefs.getValue()));
      VcsRefType type = typeAndRefs.getKey();
      boolean multiple = typeAndRefs.getValue().size() > 1;
      Color color = getTagColor(type);
      TagIcon tagIcon = new TagIcon(myHeight, myBackground, multiple ? new Color[]{color, color} : new Color[]{color});

      String text = firstRef.getName() + (multiple ? " +" : "");
      myLabels.add(Pair.create(text, tagIcon));

      myWidth += tagIcon.getIconWidth() + metrics.stringWidth(text) + MIDDLE_PADDING;
    }
  }

  @NotNull
  public static Color getTagColor(@NotNull VcsRefType type) {
    Color color = type.getBackgroundColor();
    if (UIUtil.isUnderDarcula()) {
      color = ColorUtil.brighter(color, 2);
    }
    else {
      color = ColorUtil.darker(color, 2);
    }
    return ColorUtil.saturate(color, 5);
  }

  public void paint(@NotNull Graphics2D g2, int x, int y, int height) {
    if (myLabels.isEmpty()) return;

    GraphicsConfig config = GraphicsUtil.setupAAPainting(g2);
    g2.setFont(getReferenceFont());
    g2.setStroke(new BasicStroke(1.5f));

    FontMetrics fontMetrics = g2.getFontMetrics();

    g2.setPaint(
      new GradientPaint(x, y, new Color(myBackground.getRed(), myBackground.getGreen(), myBackground.getBlue(), 0), x + GRADIENT_WIDTH, y,
                        myBackground));
    g2.fill(new Rectangle(x, y, GRADIENT_WIDTH, height));
    x += GRADIENT_WIDTH;

    g2.setColor(myBackground);
    g2.fillRect(x, y, myWidth - GRADIENT_WIDTH, height);

    for (Pair<String, TagIcon> label : myLabels) {
      TagIcon icon = label.second;
      String text = label.first;

      icon.paintIcon(null, g2, x, y + (height - icon.getIconHeight()) / 2);
      x += icon.getIconWidth();

      g2.setColor(myForeground);
      g2.drawString(text, x, y + SimpleColoredComponent.getTextBaseLine(fontMetrics, height));
      x += fontMetrics.stringWidth(text) + MIDDLE_PADDING;
    }

    config.restore();
  }

  public Dimension getSize() {
    if (myLabels.isEmpty()) return new Dimension();
    return new Dimension(myWidth, myHeight);
  }

  protected Font getReferenceFont() {
    return TextLabelPainter.getFont();
  }
}

