// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser

class GroovydocParsingTest extends GroovyParsingTestCase {
  @Override
  String getBasePath() {
    return super.basePath + "groovydoc"
  }

  void testInlined$inlined1() throws Throwable { doTest() }

  void testInlined$inlined10() throws Throwable { doTest() }

  void testInlined$inlined2() throws Throwable { doTest() }

  void testInlined$inlined3() throws Throwable { doTest() }

  void testInlined$inlined4() throws Throwable { doTest() }

  void testInlined$inlined5() throws Throwable { doTest() }

  void testInlined$inlined6() throws Throwable { doTest() }

  void testInlined$inlined7() throws Throwable { doTest() }

  void testInlined$inlined8() throws Throwable { doTest() }

  void testInlined$inlined9() throws Throwable { doTest() }

  void testParam$param1() throws Throwable { doTest() }

  void testParam$param2() throws Throwable { doTest() }

  void testParam$param3() throws Throwable { doTest() }

  void testParam$param4() throws Throwable { doTest() }

  void testParam$param5() throws Throwable { doTest() }

  void testReferences$link1() throws Throwable { doTest() }

  void testReferences$link2() throws Throwable { doTest() }

  void testReferences$linkplain1() throws Throwable { doTest() }

  void testReferences$see1() throws Throwable { doTest() }

  void testReferences$see2() throws Throwable { doTest() }

  void testReferences$see3() throws Throwable { doTest() }

  void testReferences$see4() throws Throwable { doTest() }

  void testReferences$see5() throws Throwable { doTest() }

  void testReferences$see6() throws Throwable { doTest() }

  void testReferences$throws1() throws Throwable { doTest() }

  void testReferences$throws2() throws Throwable { doTest() }

  void testReferences$val1() throws Throwable { doTest() }

  void testReferences$val2() throws Throwable { doTest() }

  void testSimple$doc1() throws Throwable { doTest() }

  void testSimple$end1() throws Throwable { doTest() }

  void testSimple$endless1() throws Throwable { doTest() }

  void testSimple$tag2() throws Throwable { doTest() }

  void testValue$data1() throws Throwable { doTest() }

  void testValue$val3() throws Throwable { doTest() }

  void testAsterisks() { doTest() }

  void testWs() { doTest() }

  void testReturnGenerics() { doTest() }
}