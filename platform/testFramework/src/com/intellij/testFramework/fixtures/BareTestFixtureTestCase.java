// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

import static com.intellij.testFramework.TestFrameworkUtil.SKIP_HEADLESS;
import static com.intellij.testFramework.TestFrameworkUtil.SKIP_SLOW;
import static org.junit.Assume.assumeFalse;

@TestOnly
public abstract class BareTestFixtureTestCase {
  @Rule public final TestName testName = new TestName();
  @Rule public final TestRule testLoggerWatcher = TestLoggerFactory.createTestWatcher();

  private BareTestFixture myFixture;

  @Before
  public final void setupFixture() throws Exception {
    ApplicationInfoImpl.setInStressTest(TestFrameworkUtil.isPerformanceTest(null, getClass().getName()));

    boolean headless = SKIP_HEADLESS && getClass().getAnnotation(SkipInHeadlessEnvironment.class) != null;
    assumeFalse("Class '" + getClass().getName() + "' is skipped because it requires working UI environment", headless);
    boolean slow = SKIP_SLOW && getClass().getAnnotation(SkipSlowTestLocally.class) != null;
    assumeFalse("Class '" + getClass().getName() + "' is skipped because it is dog slow", slow);

    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture();
    myFixture.setUp();
    Disposer.register(getTestRootDisposable(), () -> ApplicationInfoImpl.setInStressTest(false));
  }

  @After
  public final void tearDownFixture() throws Exception {
    if (myFixture != null) {
      myFixture.tearDown();
      myFixture = null;
    }
  }

  protected final @NotNull String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(testName.getMethodName(), lowercaseFirstLetter);
  }

  protected final @NotNull Disposable getTestRootDisposable() {
    return myFixture.getTestRootDisposable();
  }
}
