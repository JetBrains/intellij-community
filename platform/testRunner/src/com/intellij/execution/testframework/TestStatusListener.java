// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public abstract class TestStatusListener {
  public static final ExtensionPointName<TestStatusListener> EP_NAME = ExtensionPointName.create("com.intellij.testStatusListener");

  public abstract void testSuiteFinished(@Nullable AbstractTestProxy root);

  public void testSuiteFinished(@Nullable AbstractTestProxy root, Project project) {
    testSuiteFinished(root);
  }

  public static void notifySuiteFinished(@Nullable AbstractTestProxy root, Project project) {
    for (TestStatusListener statusListener : EP_NAME.getExtensionList()) {
      statusListener.testSuiteFinished(root, project);
    }
  }
}
