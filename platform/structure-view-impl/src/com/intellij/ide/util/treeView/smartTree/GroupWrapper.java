// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class GroupWrapper extends CachingChildrenTreeNode<Group> {
  private static final Logger LOG = Logger.getInstance(GroupWrapper.class);
  public GroupWrapper(Project project, @NotNull Group value, @NotNull TreeModel treeModel) {
    super(project, value, treeModel);
    clearChildren();
  }

  @Override
  public void copyFromNewInstance(final @NotNull CachingChildrenTreeNode newInstance) {
    clearChildren();
    setChildren(newInstance.getChildren());
    synchronizeChildren();
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    presentation.updateFrom(getValue().getPresentation());
  }

  @Override
  public void initChildren() {
    clearChildren();
    Group group = getValue();
    Collection<TreeElement> children = group.getChildren();
    for (TreeElement child : children) {
      if (child == null) {
        LOG.error(group + " returned null child: " + children);
      }
      TreeElementWrapper childNode = createChildNode(child);
      addSubElement(childNode);
    }
  }

  @Override
  protected void performTreeActions() {
    filterChildren(myTreeModel.getFilters());
    groupChildren(myTreeModel.getGroupers());
    sortChildren(myTreeModel.getSorters());
  }
}
