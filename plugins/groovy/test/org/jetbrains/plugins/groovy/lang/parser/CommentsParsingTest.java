// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyParsingFixtureKt.groovyParsingFixture;

@TestApplication
public class CommentsParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "comments");

  @Test
  public void testBrown() { parsing.get().doTest(); }

  @Test
  public void testError1205() { parsing.get().doTest(); }

  @Test
  public void testMax() { parsing.get().doTest(); }

  @Test
  public void testNls1() { parsing.get().doTest(); }

  @Test
  public void testNls2() { parsing.get().doTest(); }

  @Test
  public void testRocher3() { parsing.get().doTest(); }

  @Test
  public void testAfterIdentifier() { parsing.get().doTest(); }
}
