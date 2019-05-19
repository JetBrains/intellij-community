// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

class GrSpreadArgumentTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  @Override
  void setUp() throws Exception {
    super.setUp()
  }

  void testThrowingOutListLiteral() {
    doTest '''
import groovy.transform.CompileStatic

@CompileStatic
class A {
    def m(int a, String b) {

    }
    def m2() {
        m(*[1,""])
    }
}
''', '''
import groovy.transform.CompileStatic

@CompileStatic
class A {
    def m(int a, String b) {

    }
    def m2() {
        m(1, "")
    }
}
'''
  }

  void testReplaceWithIndexAccess() {
    doTest '''
import groovy.transform.CompileStatic

@CompileStatic
class A {
    def m(int a, int b) {
    }
    def m2(List<Integer> a) {
        m(*a)
    }
}
''', '''
import groovy.transform.CompileStatic

@CompileStatic
class A {
    def m(int a, int b) {
    }
    def m2(List<Integer> a) {
        m(a[0], a[1])
    }
}
'''
  }

  void testReplaceWithIndexAccessOnExtractedVariable() {
    doTest '''
import groovy.transform.CompileStatic

@CompileStatic
class A {
    def m(int a, int b) {
    }
    def m2(List<Integer> a) {
        m(*1.with{[2, 3]})
    }
}
''', '''
import groovy.transform.CompileStatic

@CompileStatic
class A {
    def m(int a, int b) {
    }
    def m2(List<Integer> a) {
        def integers = 1.with { [2, 3] }
        m(integers[0], integers[1])
    }
}
'''
  }

  private void doTest(String before, String after) {
    fixture.with {
      configureByText '_.groovy', before
      enableInspections(customInspections)
      def fixes = getAllQuickFixes('_.groovy')
      assert fixes.size() == 1: before
      launchAction fixes.first()
      checkResult after
    }
  }

}