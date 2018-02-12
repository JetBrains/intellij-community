// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.SettingsProvider;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    if (elements.stream().anyMatch(Objects::isNull)) LOG.error("node contains null child: " + treeNode);
     List<TreeStructureProvider> providers = getProvidersDumbAware();
    if (!providers.isEmpty()) {
      ViewSettings settings = treeNode instanceof SettingsProvider ? ((SettingsProvider)treeNode).getSettings() : ViewSettings.DEFAULT;
      for (TreeStructureProvider provider : providers) {
        try {
          elements = provider.modify(treeNode, (Collection<AbstractTreeNode>)elements, settings);
          if (elements.stream().anyMatch(Objects::isNull)) LOG.error("provider creates null child: " + provider);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    elements.forEach(node -> node.setParent(treeNode));
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
