// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.frame.ReferencesPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

class TooltipReferencesPanel extends ReferencesPanel {
  private static final int REFS_LIMIT = 10;
  private boolean myHasGroupWithMultipleRefs;

  TooltipReferencesPanel(@NotNull VcsLogData logData, @NotNull Collection<? extends VcsRef> refs) {
    super(new VerticalFlowLayout(JBUIScale.scale(H_GAP), JBUIScale.scale(V_GAP)), REFS_LIMIT);
    VirtualFile root = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(refs)).getRoot();
    setReferences(ContainerUtil.sorted(refs, logData.getLogProvider(root).getReferenceManager().getLabelsOrderComparator()));
  }

  @Override
  public void update() {
    myHasGroupWithMultipleRefs = false;
    for (Map.Entry<VcsRefType, Collection<VcsRef>> typeAndRefs : myGroupedVisibleReferences.entrySet()) {
      if (typeAndRefs.getValue().size() > 1) {
        myHasGroupWithMultipleRefs = true;
      }
    }
    super.update();
  }

  @NotNull
  @Override
  protected Font getLabelsFont() {
    return LabelPainter.getReferenceFont();
  }

  @Nullable
  @Override
  protected Icon createIcon(@NotNull VcsRefType type, @NotNull Collection<VcsRef> refs, int refIndex, int height) {
    if (refIndex == 0) {
      Color color = type.getBackgroundColor();
      return new LabelIcon(this, height, getBackground(),
                           refs.size() > 1 ? ContainerUtil.newArrayList(color, color) : Collections.singletonList(color)) {
        @Override
        public int getIconWidth() {
          return getWidth(myHasGroupWithMultipleRefs ? 2 : 1);
        }
      };
    }
    return createEmptyIcon(height);
  }

  @NotNull
  private static Icon createEmptyIcon(int height) {
    return EmptyIcon.create(LabelIcon.getWidth(height, 2), height);
  }

  @NotNull
  @Override
  protected JBLabel createRestLabel(int restSize) {
    String gray = ColorUtil.toHex(UIManager.getColor("Button.disabledText"));
    return createLabel("<html><font color=\"#" + gray + "\">... " + restSize + " more in details pane</font></html>",
                       createEmptyIcon(getIconHeight()));
  }
}
