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

package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.SettingsProvider;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AbstractTreeStructureBase extends AbstractTreeStructure {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.AbstractTreeStructureBase");
  protected final Project myProject;

  protected AbstractTreeStructureBase(Project project) {
    myProject = project;
  }

  @Override
  public Object[] getChildElements(Object element) {
    LOG.assertTrue(element instanceof AbstractTreeNode, element != null ? element.getClass().getName() : null);
    AbstractTreeNode<?> treeNode = (AbstractTreeNode)element;
    Collection<? extends AbstractTreeNode> elements = treeNode.getChildren();
     List<TreeStructureProvider> providers = getProvidersDumbAware();
    if (!providers.isEmpty()) {
      ViewSettings settings = treeNode instanceof SettingsProvider ? ((SettingsProvider)treeNode).getSettings() : ViewSettings.DEFAULT;
      for (TreeStructureProvider provider : providers) {
        try {
          elements = provider.modify(treeNode, (Collection<AbstractTreeNode>)elements, settings);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    for (AbstractTreeNode node : elements) {
      node.setParent(treeNode);
    }

    return ArrayUtil.toObjectArray(elements);
  }

  @Override
  public boolean isValid(Object element) {
    return element instanceof AbstractTreeNode;
  }

  @Override
  public Object getParentElement(Object element) {
    if (element instanceof AbstractTreeNode){
      return ((AbstractTreeNode)element).getParent();
    }
    return null;
  }

  @Override
  @NotNull
  public NodeDescriptor createDescriptor(final Object element, final NodeDescriptor parentDescriptor) {
    return (NodeDescriptor)element;
  }

  @Override
  public AsyncResult<Object> revalidateElement(Object element) {
    return super.revalidateElement(element);
  }

  @Nullable
  public abstract List<TreeStructureProvider> getProviders();

  @Nullable
  public Object getDataFromProviders(@NotNull List<AbstractTreeNode> selectedNodes, final String dataId) {
    final List<TreeStructureProvider> providers = getProvidersDumbAware();
    if (!providers.isEmpty()) {
      for (TreeStructureProvider treeStructureProvider : providers) {
        final Object fromProvider = treeStructureProvider.getData(selectedNodes, dataId);
        if (fromProvider != null) {
          return fromProvider;
        }
      }
    }
    return null;
  }

  @NotNull
  private List<TreeStructureProvider> getProvidersDumbAware() {
    if (myProject == null) {
      return Collections.emptyList();
    }

    final List<TreeStructureProvider> providers = getProviders();
    if (providers == null) {
      return Collections.emptyList();
    }

    return DumbService.getInstance(myProject).filterByDumbAwareness(providers);
  }
}
