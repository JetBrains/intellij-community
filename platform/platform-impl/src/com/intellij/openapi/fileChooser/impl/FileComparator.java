// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Comparator;

@SuppressWarnings("rawtypes")
public final class FileComparator implements Comparator<NodeDescriptor> {
  private static final FileComparator INSTANCE = new FileComparator();

  private FileComparator() { }

  public static FileComparator getInstance() {
    return INSTANCE;
  }

  @Override
  public int compare(NodeDescriptor nodeDescriptor1, NodeDescriptor nodeDescriptor2) {
    int weight1 = getWeight(nodeDescriptor1);
    int weight2 = getWeight(nodeDescriptor2);
    if (weight1 != weight2) return weight1 - weight2;

    String node1Text = nodeDescriptor1.toString();
    String node2Text = nodeDescriptor2.toString();
    boolean isNode1Unc = node1Text.startsWith("\\\\");
    boolean isNode2Unc = node2Text.startsWith("\\\\");
    if (isNode1Unc && !isNode2Unc) return 1;
    if (isNode2Unc && !isNode1Unc) return -1;

    return node1Text.compareToIgnoreCase(node2Text);
  }

  private static int getWeight(NodeDescriptor<?> descriptor) {
    VirtualFile file = ((FileNodeDescriptor)descriptor).getElement().getFile();
    return file == null || file.isDirectory() ? 0 : 1;
  }
}