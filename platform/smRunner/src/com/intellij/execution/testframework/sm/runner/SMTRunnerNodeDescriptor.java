// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.ui.BaseTestProxyNodeDescriptor;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerNodeDescriptor extends BaseTestProxyNodeDescriptor<SMTestProxy> {
  public SMTRunnerNodeDescriptor(final Project project,
                                 final SMTestProxy testProxy,
                                 final NodeDescriptor<SMTestProxy> parentDesc) {
    super(project, testProxy, parentDesc);
  }

  @Override
  public int getWeight() {
    if (!areSuitesAlwaysOnTop()) {
      return 10;
    }
    return super.getWeight();
  }

  private boolean areSuitesAlwaysOnTop() {
    SMTestProxy.SMRootTestProxy root = getElement().getRoot();
    if (root == null) {
      return true;
    }
    TestConsoleProperties properties = root.getTestConsoleProperties();
    if (properties == null) {
      return true;
    }
    return TestConsoleProperties.SUITES_ALWAYS_ON_TOP.value(properties);
  }
}

