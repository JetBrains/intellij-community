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
public class AnnotationsParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "annotations");

  @Test
  public void testAnn1() { parsing.get().doTest(); }

  @Test
  public void testAnn2() { parsing.get().doTest(); }

  @Test
  public void testAnn3() { parsing.get().doTest(); }

  @Test
  public void testAnn4() { parsing.get().doTest(); }

  @Test
  public void testAnn5() { parsing.get().doTest(); }

  @Test
  public void testAnn6() { parsing.get().doTest(); }

  @Test
  public void testAnn7() { parsing.get().doTest(); }

  @Test
  public void testClassLiteral() { parsing.get().doTest(); }

  @Test
  public void testImportAnn() { parsing.get().doTest(); }

  @Test
  public void testPackageAnn() { parsing.get().doTest(); }

  @Test
  public void testDefAttribute() { parsing.get().doTest(); }

  @Test
  public void testLineFeedAfterRef() { parsing.get().doTest(); }

  @Test
  public void testKeywordsAttributes() { parsing.get().doTest(); }

  @Test
  public void testMess() { parsing.get().doTest(); }

  @Test
  public void testTypeParameters() { parsing.get().doTest(); }

  @Test
  public void testUnfinishedReference() { parsing.get().doTest(); }

  @Test
  public void testEmptyArrayValue() { parsing.get().doTest(); }

  @Test
  public void testTrailingCommaInArrayValue() { parsing.get().doTest(); }

  @Test
  public void testMethodParameters() { parsing.get().doTest(); }

  @Test
  public void testTypeParametersInClass() { parsing.get().doTest(); }

  @Test
  public void testTypeParametersInClassRespectNewLine() { parsing.get().doTest(); }

  @Test
  public void testTypeParametersInMethod() { parsing.get().doTest(); }

  @Test
  public void testTypeParametersInMethodRespectNewLine() { parsing.get().doTest(); }
}
