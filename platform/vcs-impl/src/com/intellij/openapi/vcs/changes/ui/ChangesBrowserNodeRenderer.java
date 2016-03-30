/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class ChangesBrowserNodeRenderer extends ColoredTreeCellRenderer {
  private final Getter<Boolean> myShowFlatten;
  private final Project myProject;
  private final IssueLinkRenderer myIssueLinkRenderer;
  private final boolean myHighlightProblems;

  public ChangesBrowserNodeRenderer(final Project project, final boolean showFlatten, final boolean highlightProblems) {
  //todo review all usages and try to use new one with Getter instead of 2 instances with final property flag
    this(project, () -> showFlatten, highlightProblems);
  }

  public ChangesBrowserNodeRenderer(final Project project, final Getter<Boolean> showFlattenGetter, final boolean highlightProblems) {
    myShowFlatten = showFlattenGetter;
    myProject = project;
    myHighlightProblems = highlightProblems;
    myIssueLinkRenderer = new IssueLinkRenderer(project, this);
  }

  public boolean isShowFlatten() {
    return myShowFlatten.get();
  }

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

  protected void appendFileName(final VirtualFile vFile, final String fileName, final Color color) {
    if (myProject.isDefault()) {
      append(fileName, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color));
      return;
    }
    final ChangesFileNameDecorator decorator = ChangesFileNameDecorator.getInstance(myProject);
    if (decorator != null) {
      decorator.appendFileName(this, vFile, fileName, color, myHighlightProblems);
    }
    else {
      append(fileName, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color));
    }
  }

  public void appendTextWithIssueLinks(final String text, final SimpleTextAttributes baseStyle) {
    myIssueLinkRenderer.appendTextWithLinks(text, baseStyle);
  }
}
