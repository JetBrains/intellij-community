// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion


import groovy.transform.CompileStatic

@CompileStatic
abstract class GrFunctionalExpressionCompletionTest extends GroovyCompletionTestBase {
  void testInjectMethodForCollection() throws Throwable { doBasicTest() }

  void testEachMethodForMapWithKeyValue() throws Throwable { doBasicTest() }

  void testEachMethodForList() throws Throwable { doBasicTest() }

  void testClosureDefaultParameterInEachMethod() throws Throwable { doBasicTest() }

  void testEachMethodForEnumRanges() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
  }

  void testEachMethodForMapWithEntry() throws Throwable { doBasicTest() }

  void testEachMethodForRanges() throws Throwable { doBasicTest() }

  void testInjectMethodForArray() throws Throwable { doBasicTest() }

  void testCompletionInEachClosure() {
    doHasVariantsTest('intValue', 'intdiv')
  }

  void testWithMethod() throws Throwable { doBasicTest() }

  void testInjectMethodForMap() throws Throwable { doBasicTest() }
}
