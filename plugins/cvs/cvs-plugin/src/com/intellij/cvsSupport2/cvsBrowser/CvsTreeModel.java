package com.intellij.cvsSupport2.cvsBrowser;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

/**
 * author: lesya
 */
public class CvsTreeModel extends DefaultTreeModel{
  private JTree myTree;
  private CvsTree myCvsTree;

  public CvsTreeModel(TreeNode root) {
    super(root, true);
  }

  public CvsTree getCvsTree() {
    return myCvsTree;
  }

  public void setCvsTree(CvsTree cvsTree) {
    myCvsTree = cvsTree;
  }

  public void setTree(JTree tree) {
    myTree = tree;
  }

  public void selectRoot(){
    myTree.addSelectionPath(myTree.getPathForRow(0));
  }
}
