// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Comparator;

public class TreeNodeComparator implements Comparator<DefaultMutableTreeNode> {

  public static final TreeNodeComparator INSTANCE = new TreeNodeComparator();

  private TreeNodeComparator() {}

  @Override
  public int compare(DefaultMutableTreeNode node1, DefaultMutableTreeNode node2) {
    if (!SystemInfo.isMac) {
      if (node1 instanceof CvsModule) {
        if (!(node2 instanceof CvsModule)) {
          return -1;
        }
      } else if (node2 instanceof CvsModule) {
        return 1;
      } else if (node1 instanceof CvsFile) {
        if (!(node2 instanceof CvsFile)) {
          return 1;
        }
      } else if (node2 instanceof CvsFile) {
        return -1;
      }
      if (!(node1 instanceof CvsElement)) {
        return 1;
      } else if (!(node2 instanceof CvsElement)) {
        return -1;
      }
    }
    if (node1 instanceof LoadingNode) {
      return 1;
    } else if (node2 instanceof LoadingNode) {
      return -1;
    }
    final String name1 = ((CvsElement)node1).getName();
    final String name2 = ((CvsElement)node2).getName();
    return StringUtil.naturalCompare(name1, name2);
  }
}
