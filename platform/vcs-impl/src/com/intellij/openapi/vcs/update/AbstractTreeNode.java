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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;

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
public abstract class AbstractTreeNode extends DefaultMutableTreeNode{
  protected static final ArrayList<File> EMPTY_FILE_ARRAY = new ArrayList<File>();
  DefaultTreeModel myTreeModel;
  private JTree myTree;
  private String myErrorText;

  public void setTree(JTree tree) {
    myTree = tree;
    if (children == null) return;
    for (Iterator each = children.iterator(); each.hasNext();) {
      AbstractTreeNode node = (AbstractTreeNode) each.next();
      node.setTree(tree);
    }

  }

  public void setTreeModel(DefaultTreeModel treeModel) {
    myTreeModel = treeModel;
    if (children == null) return;
    for (Iterator each = children.iterator(); each.hasNext();) {
      AbstractTreeNode node = (AbstractTreeNode) each.next();
      node.setTreeModel(treeModel);
    }
  }

  public void setErrorText(final String errorText) {
    myErrorText = errorText;
  }

  public String getErrorText() {
    return myErrorText;
  }

  protected DefaultTreeModel getTreeModel() {
    return myTreeModel;
  }

  public JTree getTree() {
    return myTree;
  }

  public AbstractTreeNode() {
  }

  public String getText(){
    StringBuffer result = new StringBuffer();
    result.append(getName());
    if(showStatistics()){
      result.append(" (");
      result.append(getStatistics(getItemsCount()));
      result.append(")");
    }
    return result.toString();
  }

  private String getStatistics(int itemsCount){
    return VcsBundle.message("update.tree.node.size.statistics", itemsCount);
  }

  protected abstract String getName();
  protected abstract int getItemsCount();
  protected abstract boolean showStatistics();

  @NonNls public abstract Icon getIcon(boolean expanded);
  public abstract Collection<VirtualFile> getVirtualFiles();
  public abstract Collection<File> getFiles();

  public abstract SimpleTextAttributes getAttributes();

  public abstract boolean getSupportsDeletion();
}
