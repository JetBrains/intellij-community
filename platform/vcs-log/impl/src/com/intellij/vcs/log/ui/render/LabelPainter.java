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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class LabelPainter implements ReferencePainter {
  public static final int TOP_TEXT_PADDING = JBUI.scale(2);
  public static final int BOTTOM_TEXT_PADDING = JBUI.scale(1);
  public static final int GRADIENT_WIDTH = JBUI.scale(50);
  public static final int RIGHT_PADDING = JBUI.scale(5);
  public static final int MIDDLE_PADDING = JBUI.scale(5);
  private static final int MAX_LENGTH = 22;
  private static final String THREE_DOTS = "...";
  private static final String TWO_DOTS = "..";
  private static final String SEPARATOR = "/";

  @NotNull
  private List<Pair<String, LabelIcon>> myLabels = ContainerUtil.newArrayList();
  private int myHeight = JBUI.scale(22);
  private int myWidth = 0;
  @NotNull
  private Color myBackground = UIUtil.getTableBackground();
  @NotNull
  private Color myForeground = UIUtil.getTableForeground();

  public void customizePainter(@NotNull JComponent component,
                               @NotNull Collection<VcsRef> references,
                               @Nullable VcsLogRefManager manager,
                               @NotNull Color background,
                               @NotNull Color foreground) {
    myBackground = background;
    myForeground = foreground;

    FontMetrics metrics = component.getFontMetrics(getReferenceFont());
    myHeight = metrics.getHeight() + TOP_TEXT_PADDING + BOTTOM_TEXT_PADDING;
    myWidth = GRADIENT_WIDTH + RIGHT_PADDING;

    myLabels = ContainerUtil.newArrayList();
    if (manager == null) return;

    for (RefGroup group : manager.groupForTable(references)) {
      if (group.isExpanded()) {
        for (VcsRef ref : group.getRefs()) {
          LabelIcon labelIcon = new LabelIcon(myHeight, myBackground, ref.getType().getBackgroundColor());
          String text = shortenRefName(ref.getName());

          myLabels.add(Pair.create(text, labelIcon));
          myWidth += labelIcon.getIconWidth() + metrics.stringWidth(text) + MIDDLE_PADDING;
        }
      }
      else {

        LabelIcon labelIcon = new LabelIcon(myHeight, myBackground, getGroupColors(group));
        String text = shortenRefName(group.getName());

        myLabels.add(Pair.create(text, labelIcon));
        myWidth += labelIcon.getIconWidth() + metrics.stringWidth(text) + MIDDLE_PADDING;
      }
    }
  }

  @NotNull
  private static String shortenRefName(@NotNull String refName) {
    int textLength = refName.length();
    if (textLength > MAX_LENGTH) {
      int separatorIndex = refName.indexOf(SEPARATOR);
      if (separatorIndex > TWO_DOTS.length()) {
        refName = TWO_DOTS + refName.substring(separatorIndex);
      }
      return StringUtil.shortenTextWithEllipsis(refName, MAX_LENGTH, 0, THREE_DOTS);
    }
    return refName;
  }

  @NotNull
  public Color[] getGroupColors(@NotNull RefGroup group) {
    MultiMap<VcsRefType, VcsRef> referencesByType = ContainerUtil.groupBy(group.getRefs(), VcsRef::getType);
    Color[] colors;
    if (referencesByType.size() == 1) {
      Map.Entry<VcsRefType, Collection<VcsRef>> firstItem =
        ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(referencesByType.entrySet()));
      boolean multiple = firstItem.getValue().size() > 1;
      Color color = firstItem.getKey().getBackgroundColor();
      colors = multiple ? new Color[]{color, color} : new Color[]{color};
    }
    else {
      List<Color> colorsList = ContainerUtil.newArrayList();
      for (VcsRefType type : referencesByType.keySet()) {
        if (referencesByType.get(type).size() > 1) {
          colorsList.add(type.getBackgroundColor());
        }
        colorsList.add(type.getBackgroundColor());
      }
      colors = colorsList.toArray(new Color[colorsList.size()]);
    }
    return colors;
  }

  public void paint(@NotNull Graphics2D g2, int x, int y, int height) {
    if (myLabels.isEmpty()) return;

    GraphicsConfig config = GraphicsUtil.setupAAPainting(g2);
    g2.setFont(getReferenceFont());
    g2.setStroke(new BasicStroke(1.5f));

    x = paintFadeOut(g2, x, y, myWidth, height);

    FontMetrics fontMetrics = g2.getFontMetrics();
    for (Pair<String, LabelIcon> label : myLabels) {
      LabelIcon icon = label.second;
      String text = label.first;

      icon.paintIcon(null, g2, x, y + (height - icon.getIconHeight()) / 2);
      x += icon.getIconWidth();

      g2.setColor(myForeground);
      g2.drawString(text, x, y + SimpleColoredComponent.getTextBaseLine(fontMetrics, height));
      x += fontMetrics.stringWidth(text) + MIDDLE_PADDING;
    }

    config.restore();
  }

  public int paintFadeOut(@NotNull Graphics2D g2, int x, int y, int width, int height) {
    g2.setPaint(
      new GradientPaint(x, y, new Color(myBackground.getRed(), myBackground.getGreen(), myBackground.getBlue(), 0), x + GRADIENT_WIDTH, y,
                        myBackground));
    g2.fill(new Rectangle(x, y, GRADIENT_WIDTH, height));
    x += GRADIENT_WIDTH;

    g2.setColor(myBackground);
    g2.fillRect(x, y, width - GRADIENT_WIDTH, height);
    return x;
  }

  public Dimension getSize() {
    if (myLabels.isEmpty()) return new Dimension();
    return new Dimension(myWidth, myHeight);
  }

  @Override
  public boolean isLeftAligned() {
    return false;
  }
}

