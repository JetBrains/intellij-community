/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class GroupWrapper extends CachingChildrenTreeNode<Group> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.smartTree.GroupWrapper");
  public GroupWrapper(Project project, Group value, TreeModel treeModel) {
    super(project, value, treeModel);
    clearChildren();
  }

  @Override
  public void copyFromNewInstance(@NotNull final CachingChildrenTreeNode newInstance) {
    clearChildren();
    setChildren(newInstance.getChildren());
    synchronizeChildren();
  }

  @Override
  public void update(PresentationData presentation) {
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
