// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.details.commit;

import com.intellij.openapi.vcs.ui.FontUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.JBUI;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.ui.VcsBookmarkRef;
import com.intellij.vcs.log.ui.frame.WrappedFlowLayout;
import com.intellij.vcs.log.ui.render.BookmarkIcon;
import com.intellij.vcs.log.ui.render.LabelIcon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.vcs.log.ui.details.commit.CommitDetailsPanelKt.getCommitDetailsBackground;

public class ReferencesPanel extends JPanel {
  public static final int H_GAP = 4;
  protected static final int V_GAP = 0;

  private final int myRefsLimit;
  private @NotNull List<VcsRef> myReferences;
  protected @NotNull MultiMap<VcsRefType, VcsRef> myGroupedVisibleReferences;
  protected @NotNull List<VcsBookmarkRef> myBookmarks;

  public ReferencesPanel(int limit) {
    this(new WrappedFlowLayout(JBUIScale.scale(H_GAP), JBUIScale.scale(V_GAP)), limit);
  }

  public ReferencesPanel(LayoutManager layout, int limit) {
    super(layout);
    myRefsLimit = limit;
    myReferences = Collections.emptyList();
    myGroupedVisibleReferences = MultiMap.create();
    myBookmarks = Collections.emptyList();
    setOpaque(false);
  }

  public void setReferences(@NotNull List<VcsRef> references) {
    setReferences(references, Collections.emptyList());
  }

  public void setReferences(@NotNull List<VcsRef> references, @NotNull List<VcsBookmarkRef> bookmarks) {
    if (myReferences.equals(references) && myBookmarks.equals(bookmarks)) return;

    myReferences = references;
    myBookmarks = bookmarks;

    List<VcsRef> visibleReferences = (myRefsLimit > 0) ? ContainerUtil.getFirstItems(myReferences, myRefsLimit) : myReferences;
    myGroupedVisibleReferences = ContainerUtil.groupBy(visibleReferences, VcsRef::getType);

    update();
  }

  public void update() {
    removeAll();
    int height = getIconHeight();
    JBLabel firstLabel = null;

    for (VcsBookmarkRef bookmark : myBookmarks) {
      Icon icon = new BookmarkIcon(this, height, getBackground(), bookmark);
      JBLabel label = createLabel(bookmark.getText(), icon);
      label.setIconTextGap(JBUI.scale(2));
      addWrapped(label, firstLabel);
      if (firstLabel == null) firstLabel = label;
    }

    for (Map.Entry<VcsRefType, Collection<VcsRef>> typeAndRefs : myGroupedVisibleReferences.entrySet()) {
      VcsRefType type = typeAndRefs.getKey();
      Collection<VcsRef> refs = typeAndRefs.getValue();
      int refIndex = 0;
      for (VcsRef reference : refs) {
        Icon icon = createIcon(type, refs, refIndex, height);
        String ending = (refIndex != refs.size() - 1) ? "," : "";
        String text = reference.getName() + ending;
        JBLabel label = createLabel(text, icon);
        addWrapped(label, firstLabel);
        if (firstLabel == null) firstLabel = label;
        refIndex++;
      }
    }
    if (getHiddenReferencesSize() > 0) {
      JBLabel label = createRestLabel(getHiddenReferencesSize());
      addWrapped(label, Objects.requireNonNull(firstLabel));
    }

    setVisible(!myGroupedVisibleReferences.isEmpty() || !myBookmarks.isEmpty());
    revalidate();
    repaint();
  }

  private int getHiddenReferencesSize() {
    return (myRefsLimit > 0) ? myReferences.size() - Math.min(myReferences.size(), myRefsLimit) : 0;
  }

  protected int getIconHeight() {
    return getFontMetrics(getLabelsFont()).getHeight();
  }

  protected @NotNull JBLabel createRestLabel(int restSize) {
    return createLabel(VcsLogBundle.message("vcs.log.details.references.more.label", restSize), null);
  }

  protected @Nullable Icon createIcon(@NotNull VcsRefType type,
                                      @NotNull Collection<VcsRef> refs,
                                      int refIndex, int height) {
    if (refIndex == 0) {
      Color color = type.getBackgroundColor();
      return new LabelIcon(this, height, getBackground(),
                           refs.size() > 1 ? List.of(color, color) : Collections.singletonList(color));
    }
    return null;
  }

  private void addWrapped(@NotNull JBLabel label, @Nullable JBLabel referent) {
    if (referent == null) {
      add(label);
    }
    else {
      Wrapper wrapper = new Wrapper(label);
      wrapper.setVerticalSizeReferent(referent);
      add(wrapper);
    }
  }

  protected @NotNull JBLabel createLabel(@Nls @NotNull String text, @Nullable Icon icon) {
    JBLabel label = new JBLabel(text, icon, SwingConstants.LEFT);
    label.setFont(getLabelsFont());
    label.setIconTextGap(2);
    label.setHorizontalAlignment(SwingConstants.LEFT);
    label.setVerticalTextPosition(SwingConstants.CENTER);
    label.setCopyable(true);
    return label;
  }

  protected @NotNull Font getLabelsFont() {
    return FontUtil.getCommitMetadataFont();
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
