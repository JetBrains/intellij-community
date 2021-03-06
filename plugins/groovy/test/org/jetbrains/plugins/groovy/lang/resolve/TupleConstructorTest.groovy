// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve


import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.junit.Test

@CompileStatic
class TupleConstructorTest extends GroovyLatestTest implements HighlightingTest {

  @Test
  void 'many constructors'() {
    highlightingTest """
@groovy.transform.TupleConstructor
class Rr {
    String actionType = ""
    long referrerCode;
    String referrerUrl;
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr("")
    new Rr("", 1)
    new Rr("", 1, "groovy")
}
"""
  }

  @Test
  void 'many constructors with excludes'() {
    highlightingTest """
@groovy.transform.TupleConstructor(excludes = ['actionType'])
class Rr {
    String actionType = ""
    long referrerCode;
    String referrerUrl;
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr()
    new Rr(1)
    new Rr(1, "groovy")
}
"""
  }

  @Test
  void 'many constructors with includes'() {
    highlightingTest """
@groovy.transform.TupleConstructor(includes = ['actionType', 'referrerUrl'])
class Rr {
    String actionType = ""
    long referrerCode;
    String referrerUrl;
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr()
    new Rr("")
    new Rr("a", "groovy")
}
"""
  }


  @Test
  void 'many constructors with raw includes'() {
    highlightingTest """
@groovy.transform.TupleConstructor(includes = 'actionType,  referrerUrl ')
class Rr {
    String actionType = ""
    long referrerCode;
    String referrerUrl;
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr()
    new Rr("")
    new Rr("a", "groovy")
}
"""
  }

  @Test
  void 'includes induces order of parameters'() {
    highlightingTest """
@groovy.transform.TupleConstructor(includes = 'referrerUrl, actionType ')
class Rr {
    String actionType = ""
    long referrerCode;
    boolean referrerUrl;
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr(true, "groovy")
}
"""
  }

  @Test
  void 'internal names are not among parameters'() {
    highlightingTest """
@groovy.transform.TupleConstructor()
class Rr {
    String \$actionType = ""
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr<error>("")</error>
}
"""
  }

  @Test
  void 'include internal names'() {
    highlightingTest """
@groovy.transform.TupleConstructor(allNames = true)
class Rr {
    String \$actionType = ""
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr("")
}
"""
  }

  @Test
  void 'includes does not affect internal name'() {
    highlightingTest """
@groovy.transform.TupleConstructor(includes = "\$actionType")
class Rr {
    String \$actionType = ""
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr<error>("")</error>
}
"""
  }

  @Test
  void 'defaults removes additional constructors'() {
    highlightingTest """
@groovy.transform.TupleConstructor(defaults = false)
class Rr {
    String actionType
    long referrerCode;
    boolean referrerUrl;
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr<error>("")</error>
    new Rr<error>("", 1)</error>
    new Rr("", 1, true)
    new Rr<error>(actionType: "a", referrerUrl: true, referrerCode: 1)</error>
}
"""
  }

  @Test
  void 'defaults with superclass'() {
    highlightingTest """
class NN {
    String top
}

@groovy.transform.TupleConstructor(defaults = false, includeSuperProperties = true)
class Rr extends NN {
    String actionType
    long referrerCode;
    boolean referrerUrl;
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr<error>("")</error>
    new Rr<error>("", 1)</error>
    new Rr<error>("", 1, true)</error>
    new Rr("", "", 1, true)
    new Rr<error>(actionType: "a", referrerUrl: true, referrerCode: 1)</error>
}"""
  }

  @Test
  void 'allProperties enable JavaBean support'() {
    highlightingTest """
@groovy.transform.TupleConstructor(allProperties = true)
class Rr {
    Closure actionType
    long referrerCode;

    void setProp(String s) {

    }
    
    private int referrerId;

    boolean referrerUrl;
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr({})
    new Rr({}, 1)
    new Rr({}, 1, true)
    new Rr({}, 1, true, "")
    new Rr(actionType: {}, referrerUrl: true, referrerCode: 1, prop: "a")
}"""
  }

  @Test
  void 'allProperties do not affect superclasses'() {
    highlightingTest """
@groovy.transform.TupleConstructor(allProperties = true, includeFields = true)
class NN {
    public int r
    String s
    void setMp(boolean t) {

    }
}

@groovy.transform.TupleConstructor(allProperties = true, includeSuperFields = true, includeSuperProperties = true)
class Rr extends NN {
    Closure actionType
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr("", 1, {})
    new Rr<error>(1, true, "", {})</error>
}"""
  }

  @Test
  void 'closures in annotation have access to class members'() {
    highlightingTest """
@groovy.transform.CompileStatic
@groovy.transform.TupleConstructor(pre = { foo() }, post = { q == 1 })
class Rr {
    int q
    
    def foo() {}
}"""
  }

  @Test
  void 'simultaneous includes and excludes'() {
    highlightingTest """
@groovy.transform.TupleConstructor(<error>includes = "a"</error>, <error>excludes = ['b']</error>)
class Rr {}
"""
  }

  @Test
  void 'empty excludes'() {
    highlightingTest """
@groovy.transform.TupleConstructor(includes = "a", excludes = [])
class Rr {}
"""
  }

  @Test
  void 'wrong expressions at pre and post'() {
    highlightingTest """
@groovy.transform.TupleConstructor(pre = <error>Integer</error>, post = <error>String</error>)
class Rr {}
"""
  }

  @Test
  void 'forbidden initializer'() {
    highlightingTest """
@groovy.transform.TupleConstructor(defaults = false, includes = ['a', 'b'])
class Rr {
  String a = <error>""</error>
  int b = <error>1000</error>
  boolean c = true
}
"""
  }

  @Test
  void 'test visibility options'() {
    fixture.addFileToProject 'other.groovy', """
@groovy.transform.CompileStatic
@groovy.transform.TupleConstructor(defaults = false)
@groovy.transform.VisibilityOptions(constructor = Visibility.PRIVATE)
class Cde {
    String actionType
    long referrerCode
    boolean referrerUrl
}"""
    highlightingTest """
class X {

    @groovy.transform.CompileStatic
    static void main(String[] args) {
        def x = new <error>Cde</error>("mem", 1, true)
    }

}"""
  }

  @Test
  void 'test visibility options with value'() {
    fixture.addFileToProject 'other.groovy', """
@groovy.transform.CompileStatic
@groovy.transform.TupleConstructor(defaults = false)
@groovy.transform.VisibilityOptions(Visibility.PRIVATE)
class Cde {
    String actionType
    long referrerCode
    boolean referrerUrl
}"""
    highlightingTest """
class X {

    @groovy.transform.CompileStatic
    static void main(String[] args) {
        def x = new <error>Cde</error>("mem", 1, true)
    }

}"""
  }

  @Test
  void 'super resolve for pre'() {
    highlightingTest """
class NN { NN(String s) {} }

@groovy.transform.CompileStatic
@groovy.transform.TupleConstructor(pre = { super("") })
class Rr extends NN {
}"""
  }

  @Test
  void 'super constructor highlighting'() {
    highlightingTest """
class Nn {
    Nn(int a) {}
}

<error>@groovy.transform.TupleConstructor
class Rr extends Nn</error> {
    String actionType
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    def x = new Rr("")
}"""
  }

  @Test
  void 'pre highlighting'() {
    highlightingTest """
class NN { NN(String s) {} }

@groovy.transform.CompileStatic
@groovy.transform.TupleConstructor(<error>pre = { }</error>)
class Rr extends NN {
}"""
  }

  @Test
  void 'pre highlighting 2'() {
    highlightingTest """
class NN { }

@groovy.transform.CompileStatic
@groovy.transform.TupleConstructor(pre = { super() }, <error>callSuper = true</error>)
class Rr extends NN {
}"""
  }


  @Test
  void 'final fields in constructor'() {
    highlightingTest """
@groovy.transform.CompileStatic
@groovy.transform.TupleConstructor(includeFields = true)
class Rr {
  private final int a = 1
  private final boolean b
  String c
}

@groovy.transform.CompileStatic
static void main(String[] args) {
    new Rr<error>("", 2)</error>
    new Rr("", true)
}"""
  }

  @Test
  void 'inner class'() {
    highlightingTest """
@groovy.transform.CompileStatic
class DtoTest {

    @groovy.transform.TupleConstructor
    class Dto {
        String value
    }
    
    void useGeneratedConstructor() {
        new Dto("abc")
    }
}
"""
  }
}
