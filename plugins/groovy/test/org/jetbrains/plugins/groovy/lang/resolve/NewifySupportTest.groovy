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
package org.jetbrains.plugins.groovy.lang.resolve

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
  def a (){ return A<warning>()</warning>}
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
