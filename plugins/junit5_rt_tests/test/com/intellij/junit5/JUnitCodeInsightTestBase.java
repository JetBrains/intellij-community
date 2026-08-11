// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.testFramework.junit5.RunInEdt;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

@RunInEdt(writeIntent = true)
public abstract class JUnitCodeInsightTestBase {
  protected JavaCodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(
      new DefaultLightProjectDescriptor(IdeaTestUtil::getMockJdk21), getClass().getSimpleName()
    );
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));
    myFixture.setUp();

    // common JUnit Jupiter stub annotations
    myFixture.addClass("package org.junit.platform.commons.annotation; public @interface Testable {}");
    myFixture.addClass("package org.junit.jupiter.api; @org.junit.platform.commons.annotation.Testable public @interface Test {}");
    myFixture.addClass("package org.junit.jupiter.api; public @interface Nested {}");
    myFixture.addClass("package org.junit.jupiter.api; @org.junit.platform.commons.annotation.Testable public @interface TestFactory {}");
    myFixture.addClass("package org.junit.jupiter.params.provider; public @interface MethodSource {}");

    for (@Language("JAVA") String stub : getFrameworkStubs()) {
      myFixture.addClass(stub);
    }
  }

  /** Adds framework-specific marker stubs (e.g. JUnit 6 detection markers). Default: none (JUnit 5). */
  protected List<String> getFrameworkStubs() {
    return List.of();
  }

  @AfterEach
  void tearDown() throws Exception {
    myFixture.tearDown();
  }

  @AfterAll
  static void afterAll() {
    // CodeInsightFixture uses a light project.
    // We have to clean it up after the suite because it could conflict with tests which use TestApplication with a full project.
    LightPlatformTestCase.closeAndDeleteProject();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
  }
}
