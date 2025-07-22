// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsBookmarkRef;
import com.intellij.vcs.log.ui.details.commit.ReferencesPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

class TooltipReferencesPanel extends ReferencesPanel {
  private static final int REFS_LIMIT = 10;
  private boolean myHasGroupWithMultipleRefs;

  TooltipReferencesPanel(@NotNull VcsLogData logData,
                         @NotNull Collection<? extends VcsRef> refs,
                         @NotNull Collection<VcsBookmarkRef> bookmarks) {
    super(new VerticalFlowLayout(JBUIScale.scale(H_GAP), JBUIScale.scale(V_GAP)), REFS_LIMIT);

    List<VcsRef> sortedRefs;
    if (refs.isEmpty()) {
      sortedRefs = Collections.emptyList();
    }
    else {
      VirtualFile root = Objects.requireNonNull(ContainerUtil.getFirstItem(refs)).getRoot();
      VcsLogProvider provider = Objects.requireNonNull(logData.getLogProviders().get(root));
      sortedRefs = ContainerUtil.sorted(refs, provider.getReferenceManager().getLabelsOrderComparator());
    }
    List<VcsBookmarkRef> sortedBookmarks = ContainerUtil.sorted(bookmarks, Comparator.comparing(b -> b.getType()));

    setReferences(sortedRefs, sortedBookmarks);
  }

  @Override
  public void update() {
    myHasGroupWithMultipleRefs = false;
    for (Map.Entry<VcsRefType, Collection<VcsRef>> typeAndRefs : myGroupedVisibleReferences.entrySet()) {
      if (typeAndRefs.getValue().size() > 1) {
        myHasGroupWithMultipleRefs = true;
        break;
      }
    }
    super.update();
  }

  @Override
  protected @NotNull Font getLabelsFont() {
    return LabelPainter.getReferenceFont();
  }

  @Override
  protected @Nullable Icon createIcon(@NotNull VcsRefType type, @NotNull Collection<VcsRef> refs, int refIndex, int height) {
    if (refIndex == 0) {
      Color color = type.getBackgroundColor();
      return new LabelIcon(this, height, UIUtil.getToolTipBackground(),
                           refs.size() > 1 ? List.of(color, color) : Collections.singletonList(color)) {
        @Override
        public int getIconWidth() {
          return getWidth(myHasGroupWithMultipleRefs ? 2 : 1);
        }
      };
    }
    return createEmptyIcon(height);
  }

  private static @NotNull Icon createEmptyIcon(int height) {
    return EmptyIcon.create(LabelIcon.getWidth(height, 2), height);
  }

  @Override
  protected @NotNull JBLabel createLabel(@Nls @NotNull String text, @Nullable Icon icon) {
    JBLabel label = super.createLabel(text, icon);
    label.setForeground(UIUtil.getToolTipForeground());
    return label;
  }

  @Override
  protected @NotNull JBLabel createRestLabel(int restSize) {
    String gray = ColorUtil.toHex(UIManager.getColor("Button.disabledText"));
    String labelText = VcsLogBundle.message("vcs.log.references.more.tooltip", restSize);
    String html = HtmlChunk.text(labelText).wrapWith("font").attr("color", "#" + gray).wrapWith(HtmlChunk.html()).toString();
    return createLabel(html, createEmptyIcon(getIconHeight()));
  }
}
