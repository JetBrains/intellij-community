// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageBundle;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.util.ui.ColumnInfo;

import java.util.Comparator;

public final class ElementColumnInfo extends ColumnInfo<NodeDescriptor<?>, String> {
  public ElementColumnInfo() {
    super(CoverageBundle.message("coverage.view.element"));
  }

  @Override
  public Comparator<NodeDescriptor<?>> getComparator() {
    return AlphaComparator.INSTANCE;
  }

  @Override
  public String valueOf(NodeDescriptor node) {
    return node.toString();
  }
}
