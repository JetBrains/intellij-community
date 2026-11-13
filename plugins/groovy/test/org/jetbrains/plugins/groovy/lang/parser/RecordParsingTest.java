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
public class RecordParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "types/record");

  private void doTest() {
    parsing.get().doTest();
  }
  @Test public void testRecord1() { doTest(); }
  @Test public void testRecord2() { doTest(); }
  @Test public void testRecord3() { doTest(); }
  @Test public void testRecord4() { doTest(); }
  @Test public void testRecord5() { doTest(); }
  @Test public void testRecord6() { doTest(); }
  @Test public void testRecord7() { doTest(); }
  @Test public void testRecord8() { doTest(); }
}
