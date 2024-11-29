// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.ColumnInfo;

import java.util.Comparator;

public final class PercentageCoverageColumnInfo extends ColumnInfo<NodeDescriptor<?>, String> {
  private final int myColumnIdx;
  private final Comparator<NodeDescriptor<?>> myComparator;
  private final CoverageSuitesBundle mySuitesBundle;

  /**
   * @deprecated Use {@link PercentageCoverageColumnInfo#PercentageCoverageColumnInfo(int, String, CoverageSuitesBundle)}
   */
  @Deprecated
  public PercentageCoverageColumnInfo(int columnIdx,
                                      @NlsContexts.ColumnName String name,
                                      final CoverageSuitesBundle suitesBundle,
                                      @SuppressWarnings("unused") CoverageViewManager.StateBean stateBean) {
    this(columnIdx, name, suitesBundle);
  }

  public PercentageCoverageColumnInfo(int columnIdx,
                                      @NlsContexts.ColumnName String name,
                                      final CoverageSuitesBundle suitesBundle) {
    super(name);
    this.myColumnIdx = columnIdx;
    myComparator = (o1, o2) -> {
      final String val1 = valueOf(o1);
      final String val2 = valueOf(o2);
      if (val1 == null || val2 == null) return Comparing.compare(val1, val2);
      return PercentageParser.parse(val1).compareTo(PercentageParser.parse(val2));
    };
    mySuitesBundle = suitesBundle;
  }

  @Override
  public String valueOf(NodeDescriptor node) {
    final CoverageEngine coverageEngine = mySuitesBundle.getCoverageEngine();
    final Project project = node.getProject();
    CoverageViewExtension extension = coverageEngine.createCoverageViewExtension(project, mySuitesBundle);
    if (extension == null) return null;
    return extension.getPercentage(myColumnIdx, (AbstractTreeNode<?>)node);
  }

  @Override
  public Comparator<NodeDescriptor<?>> getComparator() {
    return myComparator;
  }
}
