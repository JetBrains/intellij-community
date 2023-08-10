// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
          AbstractTestProxy t1 = ((BaseTestProxyNodeDescriptor<?>)o1).getElement();
          AbstractTestProxy t2 = ((BaseTestProxyNodeDescriptor<?>)o2).getElement();
          if (!TestConsoleProperties.SUITES_ALWAYS_ON_TOP.value(properties) || 
              t1.isLeaf() == t2.isLeaf()) {
            return Comparing.compare(t2.getDuration(), t1.getDuration());
          }
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