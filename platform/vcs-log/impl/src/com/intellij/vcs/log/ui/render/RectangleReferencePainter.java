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

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.paint.PaintParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RectangleReferencePainter implements ReferencePainter {
  @NotNull
  private List<Pair<String, Color>> myLabels = ContainerUtil.newArrayList();
  private int myHeight = JBUI.scale(22);
  private int myWidth = 0;

  private final RectanglePainter myLabelPainter = new RectanglePainter(false) {
    @Override
    protected Font getLabelFont() {
      return getReferenceFont();
    }
  };

  @Override
  public void customizePainter(@NotNull JComponent component,
                               @NotNull Collection<VcsRef> references,
                               @Nullable VcsLogRefManager manager,
                               @NotNull Color background,
                               @NotNull Color foreground) {
    FontMetrics metrics = component.getFontMetrics(getReferenceFont());
    myHeight = metrics.getHeight() + RectanglePainter.TOP_TEXT_PADDING + RectanglePainter.BOTTOM_TEXT_PADDING;
    myWidth = 2 * PaintParameters.LABEL_PADDING;

    myLabels = ContainerUtil.newArrayList();
    if (manager == null) return;

    List<VcsRef> sorted = ContainerUtil.sorted(references, manager.getLabelsOrderComparator());

    for (Map.Entry<VcsRefType, Collection<VcsRef>> entry : ContainerUtil.groupBy(sorted, VcsRef::getType).entrySet()) {
      VcsRef ref = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(entry.getValue()));
      String text = ref.getName() + (entry.getValue().size() > 1 ? " +" : "");
      myLabels.add(Pair.create(text, entry.getKey().getBackgroundColor()));

      myWidth += myLabelPainter.calculateSize(text, metrics).getWidth() + PaintParameters.LABEL_PADDING;
    }
  }

  public void paint(@NotNull Graphics2D g2, int x, int y, int height) {
    if (myLabels.isEmpty()) return;

    GraphicsConfig config = GraphicsUtil.setupAAPainting(g2);
    g2.setFont(getReferenceFont());
    g2.setStroke(new BasicStroke(1.5f));

    FontMetrics fontMetrics = g2.getFontMetrics();

    x += PaintParameters.LABEL_PADDING;
    for (Pair<String, Color> label : myLabels) {
      Dimension size = myLabelPainter.calculateSize(label.first, fontMetrics);
      int paddingY = y + (height - size.height) / 2;
      myLabelPainter.paint(g2, label.first, x, paddingY, getLabelColor(label.second));
      x += size.width + PaintParameters.LABEL_PADDING;
    }

    config.restore();
  }

  @NotNull
  public static Color getLabelColor(@NotNull Color color) {
    if (UIUtil.isUnderDarcula()) {
      color = ColorUtil.darker(color, 6);
    }
    else {
      color = ColorUtil.brighter(color, 6);
    }
    return ColorUtil.desaturate(color, 3);
  }

  public Dimension getSize() {
    if (myLabels.isEmpty()) return new Dimension();
    return new Dimension(myWidth, myHeight);
  }

  @Override
  public boolean isLeftAligned() {
    return true;
  }
}
