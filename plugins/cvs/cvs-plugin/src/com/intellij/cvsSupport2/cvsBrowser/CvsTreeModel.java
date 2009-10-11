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
