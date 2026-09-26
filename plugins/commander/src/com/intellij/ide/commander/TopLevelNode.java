// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.commander;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 */
public final class TopLevelNode extends AbstractTreeNode {
  public TopLevelNode(Project project, @NotNull Object value) {
    super(project, value);
    myName = "[ .. ]";
    setIcon(AllIcons.Nodes.UpLevel);
  }

  @Override
  public @NotNull Collection<AbstractTreeNode<?>> getChildren() {
    return Collections.emptyList();
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
  }

}
