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
public class AnnotationsParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "annotations");

  private void doTest() {
    parsing.get().doTest();
  }

  @Test public void testAnn1() { doTest(); }
  @Test public void testAnn2() { doTest(); }
  @Test public void testAnn3() { doTest(); }
  @Test public void testAnn4() { doTest(); }
  @Test public void testAnn5() { doTest(); }
  @Test public void testAnn6() { doTest(); }
  @Test public void testAnn7() { doTest(); }
  @Test public void testClassLiteral() { doTest(); }
  @Test public void testImportAnn() { doTest(); }
  @Test public void testPackageAnn() { doTest(); }
  @Test public void testDefAttribute() { doTest(); }
  @Test public void testLineFeedAfterRef() { doTest(); }
  @Test public void testKeywordsAttributes() { doTest(); }
  @Test public void testMess() { doTest(); }
  @Test public void testTypeParameters() { doTest(); }
  @Test public void testUnfinishedReference() { doTest(); }
  @Test public void testEmptyArrayValue() { doTest(); }
  @Test public void testTrailingCommaInArrayValue() { doTest(); }
  @Test public void testMethodParameters() { doTest(); }
  @Test public void testTypeParametersInClass() { doTest(); }
  @Test public void testTypeParametersInClassRespectNewLine() { doTest(); }
  @Test public void testTypeParametersInMethod() { doTest(); }
  @Test public void testTypeParametersInMethodRespectNewLine() { doTest(); }
}
