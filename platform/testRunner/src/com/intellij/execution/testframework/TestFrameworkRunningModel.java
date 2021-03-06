// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework;

import com.intellij.execution.testframework.ui.AbstractTestTreeBuilderBase;
import com.intellij.execution.testframework.ui.BaseTestProxyNodeDescriptor;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public interface TestFrameworkRunningModel extends Disposable {
  TestConsoleProperties getProperties();

  void setFilter(@NotNull Filter<?> filter);

  boolean isRunning();

  TestTreeView getTreeView();

  AbstractTestTreeBuilderBase<?> getTreeBuilder();

  boolean hasTestSuites();

  AbstractTestProxy getRoot();

  void selectAndNotify(AbstractTestProxy testProxy);

  default Comparator<NodeDescriptor<?>> createComparator() {
    TestConsoleProperties properties = getProperties();
    Comparator<NodeDescriptor<?>> comparator;
    if (TestConsoleProperties.SORT_BY_DURATION.value(properties) && !isRunning()) {
      comparator = (o1, o2) -> {
        if (o1.getParentDescriptor() == o2.getParentDescriptor() &&
            o1 instanceof BaseTestProxyNodeDescriptor &&
            o2 instanceof BaseTestProxyNodeDescriptor) {
          final Long d1 = ((BaseTestProxyNodeDescriptor<?>)o1).getElement().getDuration();
          final Long d2 = ((BaseTestProxyNodeDescriptor<?>)o2).getElement().getDuration();
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