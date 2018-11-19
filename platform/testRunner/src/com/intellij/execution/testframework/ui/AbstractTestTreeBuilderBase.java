// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.openapi.Disposable;

public interface AbstractTestTreeBuilderBase<T extends AbstractTestProxy> extends Disposable {
  /**
   * Allow test animator to update the tree
   */
  void repaintWithParents(T testProxy);

  /**
   * Update comparator used in tree
   */
  void setTestsComparator(TestFrameworkRunningModel model);
}
