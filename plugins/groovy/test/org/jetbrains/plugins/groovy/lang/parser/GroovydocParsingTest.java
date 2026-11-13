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
public class GroovydocParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "groovydoc");

  private void doTest() {
    parsing.get().doTest();
  }

  @Test public void testInlined$inlined1() { doTest(); }
  @Test public void testInlined$inlined10() { doTest(); }
  @Test public void testInlined$inlined2() { doTest(); }
  @Test public void testInlined$inlined3() { doTest(); }
  @Test public void testInlined$inlined4() { doTest(); }
  @Test public void testInlined$inlined5() { doTest(); }
  @Test public void testInlined$inlined6() { doTest(); }
  @Test public void testInlined$inlined7() { doTest(); }
  @Test public void testInlined$inlined8() { doTest(); }
  @Test public void testInlined$inlined9() { doTest(); }
  @Test public void testParam$param1() { doTest(); }
  @Test public void testParam$param2() { doTest(); }
  @Test public void testParam$param3() { doTest(); }
  @Test public void testParam$param4() { doTest(); }
  @Test public void testParam$param5() { doTest(); }
  @Test public void testReferences$link1() { doTest(); }
  @Test public void testReferences$link2() { doTest(); }
  @Test public void testReferences$linkplain1() { doTest(); }
  @Test public void testReferences$see1() { doTest(); }
  @Test public void testReferences$see2() { doTest(); }
  @Test public void testReferences$see3() { doTest(); }
  @Test public void testReferences$see4() { doTest(); }
  @Test public void testReferences$see5() { doTest(); }
  @Test public void testReferences$see6() { doTest(); }
  @Test public void testReferences$throws1() { doTest(); }
  @Test public void testReferences$throws2() { doTest(); }
  @Test public void testReferences$val1() { doTest(); }
  @Test public void testReferences$val2() { doTest(); }
  @Test public void testSimple$doc1() { doTest(); }
  @Test public void testSimple$end1() { doTest(); }
  @Test public void testSimple$endless1() { doTest(); }
  @Test public void testSimple$tag2() { doTest(); }
  @Test public void testValue$data1() { doTest(); }
  @Test public void testValue$val3() { doTest(); }
  @Test public void testAsterisks() { doTest(); }
  @Test public void testWs() { doTest(); }
  @Test public void testReturnGenerics() { doTest(); }
}
