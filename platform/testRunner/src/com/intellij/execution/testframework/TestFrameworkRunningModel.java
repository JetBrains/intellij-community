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

package com.intellij.execution.testframework;

import com.intellij.execution.testframework.ui.AbstractTestTreeBuilderBase;
import com.intellij.execution.testframework.ui.BaseTestProxyNodeDescriptor;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Comparing;

import java.util.Comparator;

public interface TestFrameworkRunningModel extends Disposable {
  TestConsoleProperties getProperties();

  void setFilter(Filter filter);

  boolean isRunning();

  TestTreeView getTreeView();

  AbstractTestTreeBuilderBase getTreeBuilder();

  boolean hasTestSuites();

  AbstractTestProxy getRoot();

  void selectAndNotify(AbstractTestProxy testProxy);
  
  default Comparator<NodeDescriptor> createComparator() {
    TestConsoleProperties properties = getProperties();
    Comparator<NodeDescriptor> comparator;
    if (TestConsoleProperties.SORT_BY_DURATION.value(properties) && !isRunning()) {
      comparator = (o1, o2) -> {
        if (o1.getParentDescriptor() == o2.getParentDescriptor() &&
            o1 instanceof BaseTestProxyNodeDescriptor &&
            o2 instanceof BaseTestProxyNodeDescriptor) {
          final Long d1 = ((BaseTestProxyNodeDescriptor)o1).getElement().getDuration();
          final Long d2 = ((BaseTestProxyNodeDescriptor)o2).getElement().getDuration();
          return Comparing.compare(d2, d1);
        }
        return 0;
      };
    }
    else {
      comparator = TestConsoleProperties.SORT_ALPHABETICALLY.value(properties) ? AlphaComparator.INSTANCE : null;
    }
    return comparator;
  }
}