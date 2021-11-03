// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.junit5;

import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.testFramework.junit5.EdtInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtInterceptor.class)
abstract class JUnit5CodeInsightTest {
  protected JavaCodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(new DefaultLightProjectDescriptor());
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));
    myFixture.setUp();

    //init junit 5 framework
    myFixture.addClass("package org.junit.platform.commons.annotation; public @interface Testable {}");
    myFixture.addClass("package org.junit.jupiter.api; public @interface Test {}");
    myFixture.addClass("package org.junit.jupiter.api; public @interface Nested {}");
    myFixture.addClass("package org.junit.jupiter.api; public @interface TestFactory {}");
    myFixture.addClass("package org.junit.jupiter.params.provider; public @interface MethodSource {}");
  }

  @AfterEach
  void tearDown() throws Exception {
    myFixture.tearDown();
  }
}
