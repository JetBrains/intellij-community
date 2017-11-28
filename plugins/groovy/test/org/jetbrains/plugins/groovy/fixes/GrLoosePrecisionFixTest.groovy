// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

class GrLoosePrecisionFixTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  @Override
  void setUp() throws Exception {
    super.setUp()
  }

  void testIntByte() {
    doTest '''
import groovy.transform.CompileStatic

@CompileStatic
class A {
    def m2(int b) {
        byte a = b
    }
}
''', '''
import groovy.transform.CompileStatic

@CompileStatic
class A {
    def m2(int b) {
        byte a = (byte) b
    }
}
'''
  }

  void testDoubleFloat() {
    doTest '''
import groovy.transform.CompileStatic

@CompileStatic
class A {
    def m2(double b) {
        float a = b
    }
}
''', '''
import groovy.transform.CompileStatic

@CompileStatic
class A {
    def m2(double b) {
        float a = (float) b
    }
}
'''
  }

  void testBoxed() {
    doTest '''
import groovy.transform.CompileStatic

@CompileStatic
class A {
    def m2(Double b) {
        Float a = b
    }
}
''', '''
import groovy.transform.CompileStatic

@CompileStatic
class A {
    def m2(Double b) {
        Float a = (Float) b
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