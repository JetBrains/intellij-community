// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Comparator;

public final class FileComparator implements Comparator<NodeDescriptor> {
  private static final FileComparator INSTANCE = new FileComparator();

  private FileComparator() {
    // empty
  }

  public static FileComparator getInstance() {
    return INSTANCE;
  }

  @Override
  public int compare(NodeDescriptor nodeDescriptor1, NodeDescriptor nodeDescriptor2) {
    int weight1 = getWeight(nodeDescriptor1);
    int weight2 = getWeight(nodeDescriptor2);

    if (weight1 != weight2) {
      return weight1 - weight2;
    }

    return nodeDescriptor1.toString().compareToIgnoreCase(nodeDescriptor2.toString());
  }

   private static int getWeight(NodeDescriptor descriptor) {
     VirtualFile file = ((FileNodeDescriptor)descriptor).getElement().getFile();
     return file == null || file.isDirectory() ? 0 : 1;
   }
}
