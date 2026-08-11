// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.execution.testframework.ui.AbstractTestTreeBuilderBase;
import com.intellij.execution.testframework.ui.BaseTestProxyNodeDescriptor;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  default void scrollToRunningTest() { }

  private static @Nullable Integer getTextOffset(@NotNull AbstractTestProxy test, @NotNull TestConsoleProperties properties) {
    Location<?> location = test.getLocation(properties.getProject(), properties.getScope());
    if (location == null) return null;
    return location.toPsiLocation().getPsiElement().getTextOffset();
  }

  default Comparator<NodeDescriptor<?>> createComparator() {
    TestConsoleProperties properties = getProperties();
    Comparator<NodeDescriptor<?>> comparator;
    if (TestConsoleProperties.SORT_BY_DURATION.value(properties) && !isRunning()) {
      comparator = (node1, node2) -> {
        if (node1.getParentDescriptor() == node2.getParentDescriptor() &&
            node1 instanceof BaseTestProxyNodeDescriptor<?> testNodeDescriptor1 &&
            node2 instanceof BaseTestProxyNodeDescriptor<?> testNodeDescriptor2) {
          AbstractTestProxy t1 = testNodeDescriptor1.getElement();
          AbstractTestProxy t2 = testNodeDescriptor2.getElement();
          if (!TestConsoleProperties.SUITES_ALWAYS_ON_TOP.value(properties) || t1.isLeaf() == t2.isLeaf()) {
            return Comparing.compare(t2.getCustomizedDuration(properties), t1.getCustomizedDuration(properties));
          }
        }
        return 0;
      };
    }
    else if (TestConsoleProperties.SORT_BY_DECLARATION_ORDER.value(properties)) {
      comparator = (node1, node2) -> {
        if (node1.getParentDescriptor() == node2.getParentDescriptor() &&
            node1 instanceof BaseTestProxyNodeDescriptor<?> testNodeDescriptor1 &&
            node2 instanceof BaseTestProxyNodeDescriptor<?> testNodeDescriptor2) {
          AbstractTestProxy t1 = testNodeDescriptor1.getElement();
          AbstractTestProxy t2 = testNodeDescriptor2.getElement();
          if (!TestConsoleProperties.SUITES_ALWAYS_ON_TOP.value(properties) || t1.isLeaf() == t2.isLeaf()) {
            Integer offset1 = getTextOffset(t1, properties);
            Integer offset2 = getTextOffset(t2, properties);
            if (offset1 == null && offset2 == null) return 0;
            if (offset1 == null) return 1;
            if (offset2 == null) return -1;
            return Integer.compare(offset1, offset2);
          }
        }
        return 0;
      };
    }
    else {
      comparator = TestConsoleProperties.SORT_ALPHABETICALLY.value(properties) ? AlphaComparator.getInstance() : null;
    }
    return comparator;
  }
}
