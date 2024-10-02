// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.junit.Test

@CompileStatic
class MapConstructorTest extends GroovyLatestTest implements HighlightingTest {

  private void doTest(String text) {
    @Language("Groovy") String newText = text
    if (text.contains("MapConstructor")) {
      newText = "import groovy.transform.MapConstructor\n" + newText
    }
    if (text.contains("CompileStatic")) {
      newText = "import groovy.transform.CompileStatic\n" + newText
    }
    highlightingTest(newText, GroovyConstructorNamedArgumentsInspection)
  }

  @Test
  void 'explicit map constructor'() {
    doTest """
@MapConstructor
class Rr {
    String actionType = ""
    long referrerCode
    boolean referrerUrl
    
    Rr(String s) { int x = 1; }
}

@CompileStatic
static void main(String[] args) {
    new Rr(actionType: "a", referrerCode: 10, referrerUrl: true)
}
"""
  }

  @Test
  void 'no explicit map constructor'() {
    doTest """
class Rr {
    String actionType = ""
    long referrerCode
    boolean referrerUrl
    
    Rr(String s) { int x = 1; }
}

@CompileStatic
static void main(String[] args) {
    new Rr<error>(actionType: "a", referrerCode: 10, referrerUrl: true)</error>
}
"""
  }

  @Test
  void 'pre and post resolving'() {
    doTest """
class NN {}

@CompileStatic
@MapConstructor(pre = { super(); }, post = { assert referrerUrl = true })
class Rr extends NN {
    String actionType = ""
    long referrerCode
    boolean referrerUrl
    
    Rr(String s) { int x = 1; }
}

@CompileStatic
static void main(String[] args) {
    new Rr(actionType: "a", referrerCode: 10, referrerUrl: true)
}
"""
  }

  @Test
  void 'constructor parameter in pre and post'() {
    doTest """
@CompileStatic
@MapConstructor(pre = {args}, post = {args})
class MyClass {
}
"""
  }

  @Test
  void 'unknown label'() {
    doTest """
@MapConstructor(excludes = "actionType")
class Rr {
    String actionType
    long referrerCode;
    boolean referrerUrl;
}

@CompileStatic
static void main(String[] args) {
    def x = new Rr(<warning>actionType</warning>: "abc")
}"""
  }

  @Test
  void 'static property'() {
    doTest """
@MapConstructor(excludes = "actionType")
class Rr {
    String actionType
    long referrerCode;
    static boolean referrerUrl
}

@CompileStatic
static void main(String[] args) {
    def x = new Rr(referrerCode: 10, <warning>referrerUrl</warning>: true)
}"""
  }

  @Test
  void 'bean property'() {
    doTest """
@MapConstructor(allProperties = true)
class Rr {
  void setFoo(String s) {
  }
}

@CompileStatic
static void main(String[] args) {
  def x = new Rr(foo : "abc")
}"""
  }

  @Test
  void 'resolve super property'() {
    doTest """
class Nn {
  String fff
}

@MapConstructor(includeSuperProperties = true)
class Rr extends Nn {}

@CompileStatic
static void main(String[] args) {
  def x = new Rr(fff : "abc")
}"""
  }

  @Test
  void 'reduced visibility'() {
    fixture.addFileToProject 'other.groovy', """
@groovy.transform.CompileStatic
@groovy.transform.MapConstructor
@groovy.transform.VisibilityOptions(constructor = Visibility.PRIVATE)
class Cde {
    String actionType
    long referrerCode
    boolean referrerUrl
}"""
    doTest"""
class X {

    @CompileStatic
    static void main(String[] args) {
        def x = new <error>Cde</error>(referrerCode : 10)
    }

}"""
  }

  @Test
  void 'field with initializer'() {
    doTest """
@MapConstructor(includeFields = true)
class MapConstructorTestClass {
    private final Integer number = 123
}


void mapConstructorTest() {
    final o0 = new MapConstructorTestClass(number:333)
}"""
  }

  @Test
  void 'inner class'() {
    doTest """
class A {
  @MapConstructor
  class B {
    String rr
  }
  
  def foo() {
    new B(rr : "")
  }
}
"""
  }

  @Test
  void 'immutable'() {
    doTest """
import groovy.transform.Immutable

@Immutable
class P {
    String a
    def foo() {
        return new P(a: '')
    }
}
"""
  }
}
