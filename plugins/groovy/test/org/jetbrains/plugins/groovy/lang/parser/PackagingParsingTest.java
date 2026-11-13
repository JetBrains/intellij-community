// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyParsingFixtureKt.groovyParsingFixture;

@TestApplication
public class PackagingParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "packaging");

  private void doTest() {
    parsing.get().doTest();
  }

  @Test public void testPack1() { doTest(); }
  @Test public void testPack2() { doTest(); }
  @Test public void testPack3() { doTest(); }
  @Test public void testPack4() { doTest(); }
  @Test public void testPack5() { doTest(); }
  @Test public void testPack6() { doTest(); }
  @Test public void testPack7() { doTest(); }
  @Test public void testPack8() { doTest(); }
  @Test public void testPack9() { doTest(); }
  @Test public void testPack10() { doTest(); }
}
