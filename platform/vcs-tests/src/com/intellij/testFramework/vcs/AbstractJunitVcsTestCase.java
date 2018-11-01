// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.vcs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.TestLoggerFactory;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

public abstract class AbstractJunitVcsTestCase extends AbstractVcsTestCase {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  @Rule public TestName name = new TestName();
  @Rule public TestRule loggingRule = TestLoggerFactory.createTestWatcher();

  protected String getTestName() {
    return name.getMethodName();
  }

}
