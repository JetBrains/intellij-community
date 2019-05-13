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
package com.intellij.junit5;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestRunnerUtil;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.util.ThrowableRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class JUnit5CodeInsightTest {
  protected JavaCodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(new DefaultLightProjectDescriptor());
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));
    myFixture.setUp();
  }

  @AfterEach
  void tearDown() throws Exception {
    myFixture.tearDown();
  }

  protected void doTest(ThrowableRunnable<Throwable> run) {
    TestRunnerUtil.replaceIdeEventQueueSafely();
    //init junit 5 framework
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package org.junit.jupiter.api; public @interface Test {}");
      myFixture.addClass("package org.junit.jupiter.api; public @interface Nested {}");
      myFixture.addClass("package org.junit.jupiter.api; public @interface TestFactory {}");
      myFixture.addClass("package org.junit.jupiter.params.provider; public @interface MethodSource {}");
    });
    EdtTestUtil.runInEdtAndWait(run);
  }
}
