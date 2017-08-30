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
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.JBUI;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.ui.render.LabelIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.vcs.history.VcsHistoryUtil.getCommitDetailsFont;
import static com.intellij.vcs.log.ui.frame.CommitPanel.getCommitDetailsBackground;

public class ReferencesPanel extends JPanel {
  public static final int H_GAP = 4;
  protected static final int V_GAP = 0;
  public static final int PADDING = 3;

  private final int myRefsLimit;
  @NotNull private List<VcsRef> myReferences;
  @NotNull protected MultiMap<VcsRefType, VcsRef> myGroupedVisibleReferences;

  public ReferencesPanel() {
    this(new WrappedFlowLayout(JBUI.scale(H_GAP), JBUI.scale(V_GAP)), -1);
  }

  public ReferencesPanel(LayoutManager layout, int limit) {
    super(layout);
    myRefsLimit = limit;
    myReferences = Collections.emptyList();
    myGroupedVisibleReferences = MultiMap.create();
    setOpaque(false);
  }

  public void setReferences(@NotNull List<VcsRef> references) {
    myReferences = references;

    List<VcsRef> visibleReferences = (myRefsLimit > 0) ? myReferences.subList(0, Math.min(myReferences.size(), myRefsLimit)) : myReferences;
    myGroupedVisibleReferences = ContainerUtil.groupBy(visibleReferences, VcsRef::getType);

    update();
  }

  public void update() {
    removeAll();
    int height = getIconHeight();
    JBLabel firstLabel = null;

    for (Map.Entry<VcsRefType, Collection<VcsRef>> typeAndRefs : myGroupedVisibleReferences.entrySet()) {
      VcsRefType type = typeAndRefs.getKey();
      Collection<VcsRef> refs = typeAndRefs.getValue();
      int refIndex = 0;
      for (VcsRef reference : refs) {
        Icon icon = createIcon(type, refs, refIndex, height);
        String ending = (refIndex != refs.size() - 1) ? "," : "";
        String text = reference.getName() + ending;
        JBLabel label = createLabel(text, icon);
        if (firstLabel == null) {
          firstLabel = label;
          add(label);
        }
        else {
          addWrapped(label, firstLabel);
        }
        refIndex++;
      }
    }
    if (getHiddenReferencesSize() > 0) {
      JBLabel label = createRestLabel(getHiddenReferencesSize());
      addWrapped(label, ObjectUtils.assertNotNull(firstLabel));
    }
    setVisible(!myGroupedVisibleReferences.isEmpty());
    revalidate();
    repaint();
  }

  private int getHiddenReferencesSize() {
    return (myRefsLimit > 0) ? myReferences.size() - Math.min(myReferences.size(), myRefsLimit) : 0;
  }

  protected int getIconHeight() {
    return getFontMetrics(getLabelsFont()).getHeight() + JBUI.scale(PADDING);
  }

  @NotNull
  protected JBLabel createRestLabel(int restSize) {
    return createLabel("... " + restSize + " more", null);
  }

  @Nullable
  protected Icon createIcon(@NotNull VcsRefType type,
                            @NotNull Collection<VcsRef> refs,
                            int refIndex, int height) {
    if (refIndex == 0) {
      Color color = type.getBackgroundColor();
      return new LabelIcon(height, getBackground(),
                           refs.size() > 1 ? new Color[]{color, color} : new Color[]{color});
    }
    return null;
  }

  private void addWrapped(@NotNull JBLabel label, @NotNull JBLabel referent) {
    Wrapper wrapper = new Wrapper(label);
    wrapper.setVerticalSizeReferent(referent);
    add(wrapper);
  }

  @NotNull
  protected JBLabel createLabel(@NotNull String text, @Nullable Icon icon) {
    JBLabel label = new JBLabel(text, icon, SwingConstants.LEFT);
    label.setFont(getLabelsFont());
    label.setIconTextGap(0);
    label.setHorizontalAlignment(SwingConstants.LEFT);
    return label;
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
}
