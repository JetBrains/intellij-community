// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection
import org.jetbrains.plugins.groovy.codeInspection.control.finalVar.GrFinalVariableAccessInspection
import org.jetbrains.plugins.groovy.codeInspection.cs.GrPOJOInspection
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl
import org.jetbrains.plugins.groovy.util.HighlightingTest

@CompileStatic
class GroovyRecordHighlightingTest extends LightGroovyTestCase implements HighlightingTest {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_4_0_REAL_JDK

  void 'test pojo without cs'() {
    highlightingTest '''
import groovy.transform.stc.POJO

<warning>@POJO</warning>
class A {}
''', GrPOJOInspection
  }

  void 'test record definition'() {
    highlightingTest '''
record R(int a) {}'''
  }

  void 'test record field'() {
    highlightingTest '''
record R(int a) {}
def x = new R(10)
x.a()
'''
  }

  void 'test final record field'() {
    highlightingTest '''
record R(int a) {}
def x = new R(10)
<warning>x.a</warning> = 20
''', GrFinalVariableAccessInspection
  }

  void 'test default getter'() {
    highlightingTest '''
record X(String a) {}

def x = new X(a: "200")
println x.a()
'''
  }

  void 'test custom getter'() {
    highlightingTest '''
record X(String a) {
    String a() {
        return a + "20"
    }
}

def x = new X(a: "200")
println x.a()
'''
  }

  void 'test private record field'() {
    highlightingTest '''
record R(int a) {}
def x = new R(10)
x.<warning>a</warning>
x.a()
''', GroovyAccessibilityInspection
  }

  void 'test GROOVY-10305'() {
    highlightingTest """
record X(String a, int s) {
    private X {
        println 20
    }

    public X(String a, int s) {}
}"""
  }

  void 'test sealed record'() {
    highlightingTest """
<error>sealed</error> record X(int a) {
}"""
  }

  void 'test compact constructor'() {
    highlightingTest """
record X(int a) {
  <error>X</error> {}
}
"""
  }

  void 'test static field'() {
    highlightingTest """
record X(static int a, String b)
{}

new X("")
"""
  }

  void 'test no accessor for static field'() {
    highlightingTest """
record X(static int a, String b)
{}

new X("").a<warning>()</warning>
""", GroovyAssignabilityCheckInspection
  }


  void 'test map constructor'() {
    highlightingTest """
record X(static int a, String b)
{}

new X(b : "")
"""
  }

  void 'test non-immutable field'() {
    highlightingTest """
record X(<error>b</error>) {}
"""
  }


  void 'test forced immutable field'() {
    highlightingTest """
import groovy.transform.ImmutableOptions

@ImmutableOptions(knownImmutables = ['b'])
record X(b) {}
"""
  }

  void 'test do not unnecessarily load files containing records'() {
    myFixture.with {
      def file = addFileToProject'A.groovy', """
record R(int a, String c) {}
"""

      configureByText 'b.groovy', """
R r = new R(1, "")
"""
      checkHighlighting()

      assert !(file as GroovyFileImpl).contentsLoaded
    }
  }
}
