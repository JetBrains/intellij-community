/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

public abstract class BareTestFixtureTestCase {
  @Rule public final TestName myNameRule = new TestName();

  private BareTestFixture myFixture;

  @Before
  public final void setupFixture() throws Exception {
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture();
    myFixture.setUp();
  }

  @After
  public final void tearDownFixture() throws Exception {
    myFixture.tearDown();
    myFixture = null;
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