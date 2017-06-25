/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipInHeadlessEnvironment;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import static com.intellij.testFramework.PlatformTestUtil.SKIP_HEADLESS;
import static com.intellij.testFramework.PlatformTestUtil.SKIP_SLOW;
import static org.junit.Assume.assumeFalse;

public abstract class BareTestFixtureTestCase {
  public static final Logger LOG = Logger.getInstance(BareTestFixtureTestCase.class);
  @Rule public final TestName myNameRule = new TestName();

  private BareTestFixture myFixture;

  @Before
  public final void setupFixture() throws Exception {
    ApplicationInfoImpl.setInStressTest(UsefulTestCase.isPerformanceTest(null, getClass().getName()));

    boolean headless = SKIP_HEADLESS && getClass().getAnnotation(SkipInHeadlessEnvironment.class) != null;
    assumeFalse("Class '" + getClass().getName() + "' is skipped because it requires working UI environment", headless);
    boolean slow = SKIP_SLOW && getClass().getAnnotation(SkipSlowTestLocally.class) != null;
    assumeFalse("Class '" + getClass().getName() + "' is skipped because it is dog slow", slow);

    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture();
    myFixture.setUp();
    Disposer.register(getTestRootDisposable(), ()-> ApplicationInfoImpl.setInStressTest(false));
  }

  @After
  public final void tearDownFixture() throws Exception {
    if (myFixture != null) {
      myFixture.tearDown();
      myFixture = null;
    }
  }

  @NotNull
  protected final String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(myNameRule.getMethodName(), lowercaseFirstLetter);
  }

  @NotNull
  public final Disposable getTestRootDisposable() {
    return myFixture.getTestRootDisposable();
  }
}