// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.newify

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

@CompileStatic
class NewifySupportTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_5

  @Override
  void setUp() throws Exception {
    super.setUp()
    fixture.enableInspections(GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection)
    fixture.addClass('''
public class A {
  String name;
  int age;
  public A(){}
  public A(String name){}
}

public class A2 {
  String name;
  int age;
}
''')
  }

  void testAutoNewify() {
    doTestHighlighting """
@Newify
class B {
  def a = A.new()
  def i = Integer.new(1)
}
"""
    doTestHighlighting """
@Newify
class B {
  def a = A.new("B")
}
"""

    doTestHighlighting """
@Newify
class B {
  def a = A.new(name :"bar")
}
"""

    doTestHighlighting """
class B {
  @Newify
  def a = A.new(name :"bar")
}
"""

    doTestHighlighting """
class B {
  @Newify
  def a (){ return A.new(name :"bar")}
}
"""
    doTestHighlighting """
class B {
  @Newify(value = A, auto = false)
  def a (){ return A.<warning>new</warning>()}
}
"""
  }

  void testAutoNewifyImplicitConstructor() {
    doTestHighlighting """
@Newify
class B {
  def a = A2.new()
}
"""
    doTestHighlighting """
@Newify
class B {
  def a = A2.new<warning>("B")</warning>
}
"""

    doTestHighlighting """
@Newify
class B {
  def a = A2.new(name :"bar")
}
"""

    doTestHighlighting """
class B {
  @Newify(B)
  def b = B()
}
"""

    doTestHighlighting """
class B {
  @Newify
  def a = B.new()
}
"""

    doTestHighlighting """
class B2 {
  String str
  @Newify
  def a = B2.new(str: "B2")
}
"""

    doTestHighlighting """
class B {
  @Newify(value = A2, auto = false)
  def a (){ return A2.<warning>new</warning>()}
}
"""
  }

  void testNewifyByClass() {
    doTestHighlighting """
@Newify([A, Integer])
class B {
  def a = A()
  def i = Integer(1)
}
"""

    doTestHighlighting """
@Newify(A)
class B {
  def a = A("B")
}
"""

    doTestHighlighting """
@Newify(A)
class B {
  def a = A(name :"bar")
}
"""

    doTestHighlighting """
class B {
  @Newify(A)
  def a = A(name :"bar")
}
"""

    doTestHighlighting """
class B {
  @Newify(A)
  def a (){ return A(name :"bar")}
}
"""
    doTestHighlighting """
class B {
  @Newify
  def a (){ return <warning descr="Cannot resolve symbol 'A'">A</warning>()}
}
"""
  }

  void testNewifyMapLookup() {
    doTestHighlighting """
@Newify(A)
class B {
  def a = A(<caret>)
}
"""
    fixture.completeBasic()
    fixture.lookupElementStrings.with {
      assert contains("name")
      assert contains("age")
    }
  }

  void testNewifyElementsKind() {
    doTestHighlighting """
@Newify(A)
class B {
  def a = <caret>A()
}
"""
    PsiMethod method = fixture.elementAtCaret as PsiMethod
    assert method.isConstructor()
  }

  void testNewifyElementsKind2() {
    doTestHighlighting """
@Newify(A)
class B {
  def a = A.ne<caret>w()
}
"""
    PsiMethod method = fixture.elementAtCaret as PsiMethod
    assert method.isConstructor()
  }

  void testNewifyAutoLookup() {
    fixture.configureByText 'a.groovy', """
@Newify(A)
class B {
  def a = A.<caret>
}
"""
    fixture.completeBasic()
    fixture.lookupElementStrings.with {
      assert contains("new")
    }
  }

  void testNewifyLookupImplicitConstructor() {
    fixture.configureByText 'a.groovy', """
@Newify
class B {
  def b = B.<caret>
}
"""
    fixture.completeBasic()
    fixture.lookupElementStrings.with {
      assert contains("new")
    }
  }

  void testNewifyAutoMapLookup() {
    doTestHighlighting """
@Newify(A)
class B {
  def a = A.new(<caret>)
}
"""
    fixture.completeBasic()
    fixture.lookupElementStrings.with {
      assert contains("name")
      assert contains("age")
    }
  }

  void 'test newify supports regex patterns'() {
    doTestHighlighting """
@Newify(pattern = /[A-Z].*/)
class B {
  def a = A()
  def b = new A()
  def c = Integer(1)
}
"""


    doTestHighlighting """
@Newify(pattern = "A")
class B {
  def a = A("B")
}
"""
  }

  void 'test newify should not throw on incorrect regex'() {
    doTestHighlighting """
@Newify(pattern = "<error>*</error>")
class B {
  def a = <warning descr="Cannot resolve symbol 'A'">A</warning>()
}"""
  }

  void 'test non-static classes are not available in static context'() {
    doTestHighlighting """
class Z {
    class Inner {
    }

    @Newify(<error descr="Cannot reference non-static symbol 'Inner' from static context">Inner</error>)
    public static void main(String[] args) {
        def aaa = <warning descr="Cannot resolve symbol 'Inner'">Inner</warning>()
    }
}"""
  }

  void 'test no error for multiple constructors and named arguments'() {
    doTestHighlighting """
class Rr {
    Rr() {}
    Rr(s) {}
}


@Newify(pattern = /[A-Z].*/)
class C {
    static void main(String[] args) {
        def x = Rr(a: 2)
    }
}
"""
  }
}
