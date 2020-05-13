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
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public class TreeElementWrapper extends CachingChildrenTreeNode<TreeElement> {
  private static final Logger LOG = Logger.getInstance(TreeElementWrapper.class);
  public TreeElementWrapper(Project project, @NotNull TreeElement value, @NotNull TreeModel treeModel) {
    super(project, value, treeModel);
  }

  @Override
  public void copyFromNewInstance(@NotNull final CachingChildrenTreeNode oldInstance) {
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    if (((StructureViewTreeElement)getValue()).getValue() != null) {
      presentation.updateFrom(getValue().getPresentation());
    }
  }

  @Override
  public void initChildren() {
    clearChildren();
    TreeElement value = getValue();
    TreeElement[] children = value.getChildren();
    for (TreeElement child : children) {
      if (child == null) {
        LOG.error(value + " returned null child: " + Arrays.toString(children));
      }
      addSubElement(createChildNode(child));
    }
    if (myTreeModel instanceof ProvidingTreeModel) {
      Collection<NodeProvider> originalProviders = ((ProvidingTreeModel)myTreeModel).getNodeProviders();
      Collection<NodeProvider> providers = DumbService.getInstance(myProject).filterByDumbAwareness(originalProviders);
      for (NodeProvider provider : providers) {
        if (((ProvidingTreeModel)myTreeModel).isEnabled(provider)) {
          Collection<TreeElement> nodes = provider.provideNodes(value);
          for (TreeElement node : nodes) {
            if (node == null) {
              LOG.error(provider + " returned null node: " + nodes);
            }
            addSubElement(createChildNode(node));
          }
        }
      }
    }
  }

  @Override
  protected void performTreeActions() {
    filterChildren(myTreeModel.getFilters());
    groupChildren(myTreeModel.getGroupers());
    sortChildren(myTreeModel.getSorters());
  }
}
