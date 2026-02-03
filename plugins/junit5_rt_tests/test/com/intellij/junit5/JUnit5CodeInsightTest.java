// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

@RunInEdt(writeIntent = true)
public abstract class JUnit5CodeInsightTest {
  protected JavaCodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(
      new DefaultLightProjectDescriptor(IdeaTestUtil::getMockJdk21), "JUnit5CodeInsightTest"
    );
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));
    myFixture.setUp();

    //init junit 5 framework
    myFixture.addClass("package org.junit.platform.commons.annotation; public @interface Testable {}");
    myFixture.addClass("package org.junit.jupiter.api; @org.junit.platform.commons.annotation.Testable public @interface Test {}");
    myFixture.addClass("package org.junit.jupiter.api; public @interface Nested {}");
    myFixture.addClass("package org.junit.jupiter.api; @org.junit.platform.commons.annotation.Testable public @interface TestFactory {}");
    myFixture.addClass("package org.junit.jupiter.params.provider; public @interface MethodSource {}");
  }

  @AfterEach
  void tearDown() throws Exception {
    myFixture.tearDown();
  }

  @AfterAll
  static void afterAll() {
    // CodeInsightFixture use a light project.
    // We have to clean it up after suite because it could conflict with tests which use TestApplication with a full project.
    LightPlatformTestCase.closeAndDeleteProject();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
  }
}
