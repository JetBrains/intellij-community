// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes

import com.intellij.psi.impl.source.PostprocessReformattingAspect
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class CreateFieldFromJavaUsageTest extends GrHighlightingTestBase {
  private static final String BEFORE = "Before.groovy"
  private static final String AFTER = "After.groovy"
  private static final String JAVA = "Area.java"
  final String getBasePath() {
    return TestUtils.testDataPath + 'fixes/createFieldFromJava/' + getTestName(true) + '/'
  }

  @Override
  void setUp(){
    super.setUp()
    fixture.configureByFiles(JAVA, BEFORE)
    fixture.enableInspections(customInspections)
  }

  private void doTest() {
    fixture.with {
      def fixes = filterAvailableIntentions('Create field')
      assert fixes.size() == 1
      launchAction fixes.first()
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      checkResultByFile(BEFORE, AFTER, true)
    }
  }

  void testArrayBraces() {
    doTest()
  }

  void testSimple() {
    doTest()
  }

  void testUppercaseField() {
    doTest()
  }

  void testExpectedTypes() {
    doTest()
  }

  void testFromEquals() {
    doTest()
  }

  void testFromEqualsToPrimitiveType() {
    doTest()
  }

  void testInnerGeneric() {
    doTest()
  }

  void testInnerGenericArray() {
    doTest()
  }

  void testMultipleTypes() {
    doTest()
  }

  void testMultipleTypes2() {
    doTest()
  }

  void testParametricMethod() {
    doTest()
  }

  void testGroovyInheritor() {
    doTest()
  }

  void testJavaInheritor() {
    doTest()
  }

  void testTypeArgs() {
    doTest()
  }

  void testScript() {
    doTest()
  }

  void testSortByRelevance() {
    fixture.addClass'''
public class Foo { 
  public void put(Object key, Object value) {
  } 
}
'''
    doTest()
  }
}


