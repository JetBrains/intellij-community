// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.SettingsProvider;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AbstractTreeStructureBase extends AbstractTreeStructure {
  private static final Logger LOG = Logger.getInstance(AbstractTreeStructureBase.class);
  protected final Project myProject;

  protected AbstractTreeStructureBase(Project project) {
    myProject = project;
  }

  @Override
  public Object @NotNull [] getChildElements(@NotNull Object element) {
    LOG.assertTrue(element instanceof AbstractTreeNode, element.getClass().getName());
    AbstractTreeNode<?> treeNode = (AbstractTreeNode<?>)element;
    Collection<? extends AbstractTreeNode<?>> elements = treeNode.getChildren();
    if (elements.contains(null)) {
      LOG.error("node contains null child: " + treeNode + "; " + treeNode.getClass());
    }
    List<TreeStructureProvider> providers = Registry.is("allow.tree.structure.provider.in.dumb.mode") ? getProviders() : getProvidersDumbAware();
    if (providers != null && !providers.isEmpty()) {
      ViewSettings settings = treeNode instanceof SettingsProvider ? ((SettingsProvider)treeNode).getSettings() : ViewSettings.DEFAULT;
      for (TreeStructureProvider provider : providers) {
        ProgressManager.checkCanceled();
        try {
          //noinspection unchecked
          elements = provider.modify(treeNode, (Collection<AbstractTreeNode<?>>)elements, settings);
          if (elements.contains(null)) {
            LOG.error("provider creates null child: " + provider);
          }
        }
        catch (IndexNotReadyException e) {
          LOG.debug("TreeStructureProvider.modify requires indices", e);
          throw new ProcessCanceledException(e);
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
  public boolean isValid(@NotNull Object element) {
    return element instanceof AbstractTreeNode;
  }

  @Override
  public Object getParentElement(@NotNull Object element) {
    if (element instanceof AbstractTreeNode){
      return ((AbstractTreeNode<?>)element).getParent();
    }
    return null;
  }

  @Override
  @NotNull
  public NodeDescriptor<?> createDescriptor(@NotNull final Object element, final NodeDescriptor parentDescriptor) {
    return (NodeDescriptor<?>)element;
  }

  @Nullable
  public abstract List<TreeStructureProvider> getProviders();

  @Nullable
  public Object getDataFromProviders(@NotNull List<AbstractTreeNode<?>> selectedNodes, @NotNull String dataId) {
    List<TreeStructureProvider> providers = getProvidersDumbAware();
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
