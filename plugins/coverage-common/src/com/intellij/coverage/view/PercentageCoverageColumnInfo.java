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
  private final CoverageViewManager.StateBean myStateBean;

  public PercentageCoverageColumnInfo(int columnIdx,
                                      @NlsContexts.ColumnName String name,
                                      final CoverageSuitesBundle suitesBundle,
                                      CoverageViewManager.StateBean stateBean) {
    super(name);
    this.myColumnIdx = columnIdx;
    myComparator = (o1, o2) -> {
      final String val1 = valueOf(o1);
      final String val2 = valueOf(o2);
      if (val1 != null && val2 != null) {
        final int percentageIndex1 = val1.indexOf('%');
        final int percentageIndex2 = val2.indexOf('%');
        if (percentageIndex1 > -1 && percentageIndex2 >-1) {
          final String percentage1 = val1.substring(0, percentageIndex1);
          final String percentage2 = val2.substring(0, percentageIndex2);
          final int compare = Double.compare(safeParseDouble(percentage1), safeParseDouble(percentage2));
          if (compare == 0) {
            final int total1 = val1.indexOf('/');
            final int total2 = val2.indexOf('/');
            if (total1 > -1 && total2 > -1) {
              final int r1 = val1.indexOf(')', total1);
              final int r2 = val2.indexOf(')', total2);
              if (r1 > -1 && r2 > -1) {
                return Double.compare(safeParseDouble(val1.substring(total1 + 1, r1)),
                                      safeParseDouble(val2.substring(total2 + 1, r2)));
              }
            }
          }
          return compare;
        }
        if (percentageIndex1 > -1) return 1;
        if (percentageIndex2 > -1) return -1;
      }
      return Comparing.compare(val1, val2);
    };
    mySuitesBundle = suitesBundle;
    myStateBean = stateBean;
  }

  @Override
  public String valueOf(NodeDescriptor node) {
    final CoverageEngine coverageEngine = mySuitesBundle.getCoverageEngine();
    final Project project = node.getProject();
    return coverageEngine.createCoverageViewExtension(project, mySuitesBundle, myStateBean).getPercentage(myColumnIdx, (AbstractTreeNode<?>)node);
  }

  @Override
  public Comparator<NodeDescriptor<?>> getComparator() {
    return myComparator;
  }

  static double safeParseDouble(String s) {
    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException e) {
      int begin = 0;
      int end = -1;
      for (int i = s.length(); --i >= 0;) {
        final char c = s.charAt(i);
        final boolean isDigit = '0' <= c && c <= '9' || c == '.';
        if (isDigit) {
          if (end == -1) {
            end = i + 1;
          }
          begin = i;
        } else {
          if (end == -1) continue;
          break;
        }
      }
      if (end == -1) return 0.0;
      try {
        return Double.parseDouble(s.substring(begin, end));
      } catch (NumberFormatException e1) {
        return 0.0;
      }
    }
  }
}
