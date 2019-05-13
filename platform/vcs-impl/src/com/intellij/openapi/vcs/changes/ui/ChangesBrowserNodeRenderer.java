// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ChangesBrowserNodeRenderer extends ColoredTreeCellRenderer {

  @NotNull private final BooleanGetter myShowFlatten;
  @Nullable private final Project myProject;
  @Nullable private final IssueLinkRenderer myIssueLinkRenderer;
  private final boolean myHighlightProblems;
  @Nullable private JBInsets myBackgroundInsets;

  public ChangesBrowserNodeRenderer(@Nullable Project project, @NotNull BooleanGetter showFlattenGetter, boolean highlightProblems) {
    myShowFlatten = showFlattenGetter;
    myProject = project;
    myHighlightProblems = highlightProblems;
    myIssueLinkRenderer = project != null ? new IssueLinkRenderer(project, this) : null;
  }

  public boolean isShowFlatten() {
    return myShowFlatten.get();
  }

  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    ChangesBrowserNode node = (ChangesBrowserNode)value;
    node.render(this, selected, expanded, hasFocus);
    SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, selected);
  }

  protected void appendFileName(@Nullable VirtualFile vFile, @NotNull String fileName, Color color) {
    ChangesFileNameDecorator decorator = myProject != null && !myProject.isDefault()
                                         ? ChangesFileNameDecorator.getInstance(myProject) : null;

    if (decorator != null) {
      decorator.appendFileName(this, vFile, fileName, color, myHighlightProblems);
    }
    else {
      append(fileName, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color));
    }
  }

  @Override
  public void clear() {
    setBackgroundInsets(null);
    setToolTipText(null);
    super.clear();
  }

  @Override
  protected void doPaintFragmentBackground(@NotNull Graphics2D g, int index, @NotNull Color bgColor, int x, int y, int width, int height) {
    if (myBackgroundInsets != null) {
      g.setColor(bgColor);
      g.fillRect(x + myBackgroundInsets.left, y + myBackgroundInsets.top, width - myBackgroundInsets.width(),
                 height - myBackgroundInsets.height());
    }
    else {
      super.doPaintFragmentBackground(g, index, bgColor, x, y, width, height);
    }
  }

  public void appendTextWithIssueLinks(@NotNull String text, @NotNull SimpleTextAttributes baseStyle) {
    if (myIssueLinkRenderer != null) {
      myIssueLinkRenderer.appendTextWithLinks(text, baseStyle);
    }
    else {
      append(text, baseStyle);
    }
  }

  public void setIcon(@NotNull FileType fileType, boolean isDirectory) {
    Icon icon = isDirectory ? PlatformIcons.FOLDER_ICON : fileType.getIcon();
    setIcon(icon);
  }

  public void setBackgroundInsets(@Nullable JBInsets backgroundInsets) {
    myBackgroundInsets = backgroundInsets;
  }

  public boolean isShowingLocalChanges() {
    return myHighlightProblems;
  }
}
