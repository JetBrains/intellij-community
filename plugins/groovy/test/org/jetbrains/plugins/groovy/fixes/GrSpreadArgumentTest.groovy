/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.fixes

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

class GrSpreadArgumentTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

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