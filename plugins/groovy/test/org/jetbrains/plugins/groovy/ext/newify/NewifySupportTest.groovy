// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.newify

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

@CompileStatic
class NewifySupportTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

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
    testHighlighting """
@Newify
class B {
  def a = A.new()
  def i = Integer.new(1)
}
"""
    testHighlighting """
@Newify
class B {
  def a = A.new("B")
}
"""

    testHighlighting """
@Newify
class B {
  def a = A.new(name :"bar")
}
"""

    testHighlighting """
class B {
  @Newify
  def a = A.new(name :"bar")
}
"""

    testHighlighting """
class B {
  @Newify
  def a (){ return A.new(name :"bar")}
}
"""
    testHighlighting """
class B {
  @Newify(value = A, auto = false)
  def a (){ return A.<warning>new</warning>()}
}
"""
  }

  void testAutoNewifyImplicitConstructor() {
    testHighlighting """
@Newify
class B {
  def a = A2.new()
}
"""
    testHighlighting """
@Newify
class B {
  def a = A2.new<warning>("B")</warning>
}
"""

    testHighlighting """
@Newify
class B {
  def a = A2.new(name :"bar")
}
"""

    testHighlighting """
class B {
  @Newify(B)
  def b = B()
}
"""

    testHighlighting """
class B {
  @Newify
  def a = B.new()
}
"""

    testHighlighting """
class B2 {
  String str
  @Newify
  def a = B2.new(str: "B2")
}
"""

    testHighlighting """
class B {
  @Newify(value = A2, auto = false)
  def a (){ return A2.<warning>new</warning>()}
}
"""
  }

  void testNewifyByClass() {
    testHighlighting """
@Newify([A, Integer])
class B {
  def a = A()
  def i = Integer(1)
}
"""

    testHighlighting """
@Newify(A)
class B {
  def a = A("B")
}
"""

    testHighlighting """
@Newify(A)
class B {
  def a = A(name :"bar")
}
"""

    testHighlighting """
class B {
  @Newify(A)
  def a = A(name :"bar")
}
"""

    testHighlighting """
class B {
  @Newify(A)
  def a (){ return A(name :"bar")}
}
"""
    testHighlighting """
class B {
  @Newify
  def a (){ return <warning descr="Cannot resolve symbol 'A'">A</warning>()}
}
"""
  }

  void testNewifyMapLookup() {
    testHighlighting """
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
    testHighlighting """
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
}
