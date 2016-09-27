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

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer;
import com.intellij.vcs.log.ui.render.LabelIcon;
import com.intellij.vcs.log.ui.render.RectanglePainter;
import com.intellij.vcs.log.ui.render.RectangleReferencePainter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.vcs.history.VcsHistoryUtil.getCommitDetailsFont;
import static com.intellij.vcs.log.ui.frame.CommitPanel.getCommitDetailsBackground;

public class ReferencesPanel extends JPanel {
  public static final int H_GAP = 4;
  private static final int V_GAP = 0;
  public static final int PADDING = 3;

  private final int myRefsLimit;
  @NotNull private List<VcsRef> myReferences;

  public ReferencesPanel() {
    this(-1);
  }

  public ReferencesPanel(int limit) {
    super(new WrappedFlowLayout(JBUI.scale(H_GAP), JBUI.scale(V_GAP)));
    myRefsLimit = limit;
    myReferences = Collections.emptyList();
    setOpaque(false);
  }

  public void setReferences(@NotNull List<VcsRef> references) {
    myReferences = references;
    update();
  }

  public void update() {
    removeAll();
    int height = getFontMetrics(getLabelsFont()).getHeight() + JBUI.scale(PADDING);
    JBLabel firstLabel = null;
    List<VcsRef> visibleReferences = (myRefsLimit > 0) ? myReferences.subList(0, Math.min(myReferences.size(), myRefsLimit)) : myReferences;
    String tail =
      myReferences.size() > visibleReferences.size() ? "... (" + (myReferences.size() - visibleReferences.size()) + " more)" : "";

    int typeIndex = 0;
    Set<Map.Entry<VcsRefType, Collection<VcsRef>>> groupedByType = ContainerUtil.groupBy(visibleReferences, VcsRef::getType).entrySet();
    for (Map.Entry<VcsRefType, Collection<VcsRef>> typeAndRefs : groupedByType) {
      if (GraphCommitCellRenderer.isRedesignedLabels()) {
        VcsRefType type = typeAndRefs.getKey();
        int refIndex = 0;
        for (VcsRef reference : typeAndRefs.getValue()) {
          Icon icon = null;
          if (refIndex == 0) {
            Color color = type.getBackgroundColor();
            icon = new LabelIcon(height, getBackground(),
                                 typeAndRefs.getValue().size() > 1 ? new Color[]{color, color} : new Color[]{color});
          }
          String ending = (refIndex != typeAndRefs.getValue().size() - 1) ? "," : ((typeIndex != groupedByType.size() - 1) ? "" : tail);
          JBLabel label = new JBLabel(reference.getName() + ending, icon, SwingConstants.LEFT);
          label.setFont(getLabelsFont());
          label.setIconTextGap(0);
          label.setHorizontalAlignment(SwingConstants.LEFT);
          if (firstLabel == null) {
            firstLabel = label;
            add(label);
          }
          else {
            Wrapper wrapper = new Wrapper(label);
            wrapper.setVerticalSizeReferent(firstLabel);
            add(wrapper);
          }
          refIndex++;
        }
        typeIndex++;
      }
      else {
        for (VcsRef reference : typeAndRefs.getValue()) {
          add(new ReferencePanel(reference));
        }
      }
    }
    setVisible(!visibleReferences.isEmpty());
    revalidate();
    repaint();
  }

  @NotNull
  protected Font getLabelsFont() {
    return getCommitDetailsFont();
  }

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(super.getMaximumSize().width, super.getPreferredSize().height);
  }

  @Override
  public Color getBackground() {
    return getCommitDetailsBackground();
  }

  @NotNull
  public WrappedFlowLayout getLayout() {
    return (WrappedFlowLayout)super.getLayout();
  }

  private static class ReferencePanel extends JPanel {
    @NotNull private final RectanglePainter myLabelPainter;
    @NotNull private final VcsRef myReference;

    private ReferencePanel(@NotNull VcsRef reference) {
      myReference = reference;
      myLabelPainter = new RectanglePainter(false);
      setOpaque(false);
    }

    @Override
    public void paint(Graphics g) {
      myLabelPainter.paint((Graphics2D)g, myReference.getName(), 0, 0,
                           RectangleReferencePainter.getLabelColor(myReference.getType().getBackgroundColor()));
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension dimension = myLabelPainter.calculateSize(myReference.getName(), getFontMetrics(RectanglePainter.getFont()));
      return new Dimension(dimension.width, dimension.height + JBUI.scale(PADDING));
    }

    @Override
    public Color getBackground() {
      return getCommitDetailsBackground();
    }
  }
}
