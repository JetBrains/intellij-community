// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.Disposable;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestLoggerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

@TestOnly
public abstract class BareTestFixtureTestCase {
  @Rule
  public final TestFixtureRule testFixtureRule = new TestFixtureRule();
  @Rule
  public final TestName testName = new TestName();
  @Rule
  public final TestRule testLoggerWatcher = TestLoggerFactory.createTestWatcher();


  protected final @NotNull String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(testName.getMethodName(), lowercaseFirstLetter);
  }

  protected final @NotNull Disposable getTestRootDisposable() {
    return testFixtureRule.getFixture().getTestRootDisposable();
  }
}
