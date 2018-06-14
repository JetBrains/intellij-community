/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ui.ColumnInfo;

import java.util.Comparator;

public class PercentageCoverageColumnInfo extends ColumnInfo<NodeDescriptor, String> {
  private final int myColumnIdx;
  private final Comparator<NodeDescriptor> myComparator;
  private final CoverageSuitesBundle mySuitesBundle;
  private final CoverageViewManager.StateBean myStateBean;

  public PercentageCoverageColumnInfo(int columnIdx,
                               String name,
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
          final int compare = Comparing.compare(Double.parseDouble(percentage1), Double.parseDouble(percentage2));
          if (compare == 0) {
            final int total1 = val1.indexOf('/');
            final int total2 = val2.indexOf('/');
            if (total1 > -1 && total2 > -1) {
              final int r1 = val1.indexOf(')', total1);
              final int r2 = val2.indexOf(')', total2);
              if (r1 > -1 && r2 > -1) {
                return Comparing.compare(Double.parseDouble(val1.substring(total1 + 1, r1)),
                                         Double.parseDouble(val2.substring(total2 + 1, r2)));
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
    return coverageEngine.createCoverageViewExtension(project, mySuitesBundle, myStateBean).getPercentage(myColumnIdx, (AbstractTreeNode)node);
  }

  @Override
  public Comparator<NodeDescriptor> getComparator() {
    return myComparator;
  }
}
