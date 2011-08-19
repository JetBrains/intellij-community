/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
