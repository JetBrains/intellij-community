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

import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.frame.ReferencesPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Map;

class TooltipReferencesPanel extends ReferencesPanel {
  private static final int REFS_LIMIT = 10;
  @NotNull private final LabelPainter myReferencePainter;
  private boolean myHasGroupWithMultipleRefs;

  public TooltipReferencesPanel(@NotNull VcsLogData logData,
                                @NotNull LabelPainter referencePainter,
                                @NotNull Collection<VcsRef> refs) {
    super(new VerticalFlowLayout(JBUI.scale(H_GAP), JBUI.scale(V_GAP)), REFS_LIMIT);
    myReferencePainter = referencePainter;

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
    return myReferencePainter.getReferenceFont();
  }

  @Nullable
  @Override
  protected Icon createIcon(@NotNull VcsRefType type, @NotNull Collection<VcsRef> refs, int refIndex, int height) {
    if (refIndex == 0) {
      Color color = EditorColorsUtil.getGlobalOrDefaultColor(type.getBgColorKey());
      return new LabelIcon(height, getBackground(),
                           refs.size() > 1 ? new Color[]{color, color} : new Color[]{color}) {
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
