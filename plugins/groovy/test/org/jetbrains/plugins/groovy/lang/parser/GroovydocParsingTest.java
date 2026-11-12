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
public class GroovydocParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "groovydoc");

  @Test
  public void testInlined$inlined1() { parsing.get().doTest(); }

  @Test
  public void testInlined$inlined10() { parsing.get().doTest(); }

  @Test
  public void testInlined$inlined2() { parsing.get().doTest(); }

  @Test
  public void testInlined$inlined3() { parsing.get().doTest(); }

  @Test
  public void testInlined$inlined4() { parsing.get().doTest(); }

  @Test
  public void testInlined$inlined5() { parsing.get().doTest(); }

  @Test
  public void testInlined$inlined6() { parsing.get().doTest(); }

  @Test
  public void testInlined$inlined7() { parsing.get().doTest(); }

  @Test
  public void testInlined$inlined8() { parsing.get().doTest(); }

  @Test
  public void testInlined$inlined9() { parsing.get().doTest(); }

  @Test
  public void testParam$param1() { parsing.get().doTest(); }

  @Test
  public void testParam$param2() { parsing.get().doTest(); }

  @Test
  public void testParam$param3() { parsing.get().doTest(); }

  @Test
  public void testParam$param4() { parsing.get().doTest(); }

  @Test
  public void testParam$param5() { parsing.get().doTest(); }

  @Test
  public void testReferences$link1() { parsing.get().doTest(); }

  @Test
  public void testReferences$link2() { parsing.get().doTest(); }

  @Test
  public void testReferences$linkplain1() { parsing.get().doTest(); }

  @Test
  public void testReferences$see1() { parsing.get().doTest(); }

  @Test
  public void testReferences$see2() { parsing.get().doTest(); }

  @Test
  public void testReferences$see3() { parsing.get().doTest(); }

  @Test
  public void testReferences$see4() { parsing.get().doTest(); }

  @Test
  public void testReferences$see5() { parsing.get().doTest(); }

  @Test
  public void testReferences$see6() { parsing.get().doTest(); }

  @Test
  public void testReferences$throws1() { parsing.get().doTest(); }

  @Test
  public void testReferences$throws2() { parsing.get().doTest(); }

  @Test
  public void testReferences$val1() { parsing.get().doTest(); }

  @Test
  public void testReferences$val2() { parsing.get().doTest(); }

  @Test
  public void testSimple$doc1() { parsing.get().doTest(); }

  @Test
  public void testSimple$end1() { parsing.get().doTest(); }

  @Test
  public void testSimple$endless1() { parsing.get().doTest(); }

  @Test
  public void testSimple$tag2() { parsing.get().doTest(); }

  @Test
  public void testValue$data1() { parsing.get().doTest(); }

  @Test
  public void testValue$val3() { parsing.get().doTest(); }

  @Test
  public void testAsterisks() { parsing.get().doTest(); }

  @Test
  public void testWs() { parsing.get().doTest(); }

  @Test
  public void testReturnGenerics() { parsing.get().doTest(); }
}
