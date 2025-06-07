// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSetBase;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * author: lesya
 */
@ApiStatus.Internal
public abstract class AbstractTreeNode extends DefaultMutableTreeNode {
  protected static final ArrayList<File> EMPTY_FILE_ARRAY = new ArrayList<>();
  DefaultTreeModel myTreeModel;
  private JTree myTree;
  private @Nls String myErrorText;
  protected SimpleTextAttributes myFilterAttributes;

  public void setTree(JTree tree) {
    myTree = tree;
    if (children == null) return;
    for (Object aChildren : children) {
      AbstractTreeNode node = (AbstractTreeNode)aChildren;
      node.setTree(tree);
    }
  }

  public void setTreeModel(DefaultTreeModel treeModel) {
    myTreeModel = treeModel;
    if (children == null) return;
    for (Object aChildren : children) {
      AbstractTreeNode node = (AbstractTreeNode)aChildren;
      node.setTreeModel(treeModel);
    }
  }

  public void setErrorText(@Nls String errorText) {
    myErrorText = errorText;
  }

  public @Nls String getErrorText() {
    return myErrorText;
  }

  protected boolean acceptFilter(@Nullable Pair<PackageSetBase, NamedScopesHolder> filter, boolean showOnlyFilteredItems) {
    boolean apply = false;
    if (children != null && filter != null) {
      for (Iterator it = children.iterator(); it.hasNext(); ) {
        AbstractTreeNode node = (AbstractTreeNode)it.next();
        if (node.acceptFilter(filter, showOnlyFilteredItems)) {
          apply = true;
        }
        else if (showOnlyFilteredItems) {
          if (node instanceof Disposable) {
            Disposer.dispose((Disposable)node);
          }
          it.remove();
        }
      }
      applyFilter(apply);
    }
    return apply;
  }

  protected void applyFilter(boolean apply) {
    myFilterAttributes = apply ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : null;
  }

  protected DefaultTreeModel getTreeModel() {
    return myTreeModel;
  }

  public JTree getTree() {
    return myTree;
  }

  public AbstractTreeNode() {
  }

  public @Nls String getText() {
    @Nls StringBuilder result = new StringBuilder();
    result.append(getName());
    if (showStatistics()) {
      result.append(" (");
      result.append(getStatistics(getItemsCount()));
      result.append(")");
    }
    return result.toString();
  }

  private static @Nls String getStatistics(int itemsCount) {
    return VcsBundle.message("update.tree.node.size.statistics", itemsCount);
  }

  protected abstract @Nls @NotNull String getName();

  protected abstract int getItemsCount();

  protected abstract boolean showStatistics();

  public abstract @NonNls Icon getIcon(boolean expanded);

  public abstract @NotNull Collection<VirtualFile> getVirtualFiles();

  public abstract @NotNull Collection<File> getFiles();

  public abstract @NotNull SimpleTextAttributes getAttributes();

  public abstract boolean getSupportsDeletion();
}
