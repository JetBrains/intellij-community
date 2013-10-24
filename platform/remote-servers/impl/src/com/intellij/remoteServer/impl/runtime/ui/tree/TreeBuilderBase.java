package com.intellij.remoteServer.impl.runtime.ui.tree;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.ide.util.treeView.IndexComparator;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public class TreeBuilderBase extends AbstractTreeBuilder {
  public TreeBuilderBase(JTree tree, AbstractTreeStructureBase structure, DefaultTreeModel treeModel) {
    super(tree, treeModel, structure, IndexComparator.INSTANCE);
    initRootNode();
  }
}
