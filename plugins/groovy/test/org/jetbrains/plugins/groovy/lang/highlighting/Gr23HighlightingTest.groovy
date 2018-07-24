// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

/**
 * Created by Max Medvedev on 27/02/14
 */
class Gr23HighlightingTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_3

  @Override
  InspectionProfileEntry[] getCustomInspections() {
    [new GroovyAssignabilityCheckInspection(), new GrUnresolvedAccessInspection()]
  }

  void assertScript(String text) {
    testHighlighting("import groovy.transform.CompileStatic\nimport groovy.transform.stc.ClosureParams\n" + text)
  }

  void shouldFailWithMessages(String text) {
    assertScript(text)
  }

  void testInferenceOnNonExtensionMethod() {
    assertScript '''
import groovy.transform.stc.FirstParam

public <T> T foo(T arg, @ClosureParams(FirstParam) Closure c) { c.call(arg) }

@CompileStatic
def test() {
    assert foo('a') { it.toUpperCase() } == 'A'
}
'''
  }

  void testFromStringWithSimpleType() {
    assertScript '''
import groovy.transform.stc.FromString

void foo(@ClosureParams(value=FromString,options="java.lang.String") Closure cl) { cl.call('foo') }

@CompileStatic
def test() {
    foo { String str -> println str.toUpperCase()}
}
'''

    shouldFailWithMessages '''
import groovy.transform.stc.FromString

void foo(@ClosureParams(value=FromString,options="java.lang.String") Closure cl) { cl.call('foo') }

@CompileStatic
def test() {
    foo { <error descr="Expected 'java.lang.String', found 'java.util.Date'">Date</error> str -> println str}
}
'''
  }

  void testFromStringWithGenericType() {
    assertScript '''
import groovy.transform.stc.FromString

void foo(@ClosureParams(value=FromString,options="java.util.List<java.lang.String>") Closure cl) { cl.call(['foo']) }

@CompileStatic
def test() {
    foo { List<String> str -> str.each { println it.toUpperCase() } }
}
'''

    shouldFailWithMessages '''
import groovy.transform.stc.FromString

void foo(@ClosureParams(value=FromString,options="java.util.List<java.lang.String>") Closure cl) { cl.call(['foo']) }

@CompileStatic
def test() {
    foo { <error descr="Expected 'java.util.List<java.lang.String>', found 'java.util.List<java.util.Date>'">List<Date></error> d -> d.each { println it } }
}
'''
  }

  void testFromStringWithDirectGenericPlaceholder() {

    assertScript '''
import groovy.transform.stc.FromString

public <T> void foo(T t, @ClosureParams(value=FromString,options="T") Closure cl) { cl.call(t) }

@CompileStatic
def test() {
    foo('hey') { println it.toUpperCase() }
}
'''

  }

  void testFromStringWithGenericPlaceholder() {
    assertScript '''
import groovy.transform.stc.FromString

public <T> void foo(T t, @ClosureParams(value=FromString,options="java.util.List<T>") Closure cl) { cl.call([t,t]) }

@CompileStatic
def test() {
    foo('hey') { List<String> str -> str.each { println it.toUpperCase() } }
}
'''

  }

  void testFromStringWithGenericPlaceholderFromClass() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo<T> {
    public void foo(@ClosureParams(value=FromString,options="java.util.List<T>") Closure cl) { cl.call(['hey','ya']) }
}

@CompileStatic
def test() {
    def foo = new Foo<String>()

    foo.foo { List<String> str -> str.each { println it.toUpperCase() } }
}'''
  }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenerics() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo<T,U> {
    public void foo(@ClosureParams(value=FromString,options="java.util.List<U>") Closure cl) { cl.call(['hey','ya']) }
}

@CompileStatic
def test() {
    def foo = new Foo<Integer,String>()

    foo.foo { List<String> str -> str.each { println it.toUpperCase() } }
}'''
  }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignature() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo<T,U> {
    public void foo(@ClosureParams(value=FromString,options="java.util.List<U>") Closure cl) { cl.call(['hey','ya']) }
}

@CompileStatic
def test() {
    def foo = new Foo<Integer,String>()

    foo.foo { it.each { println it.toUpperCase() } }
}'''
  }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQN() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo<T,U> {
    public void foo(@ClosureParams(value=FromString,options="List<U>") Closure cl) { cl.call(['hey','ya']) }
}

@CompileStatic
def test() {
    def foo = new Foo<Integer,String>()

    foo.foo { it.each { println it.toUpperCase() } }
}'''
  }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQNAndReferenceToSameUnitClass() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo {
    void bar() {
        println 'Haha!'
    }
}

class Tor<D,U> {
    public void foo(@ClosureParams(value=FromString,options="List<U>") Closure cl) { cl.call([new Foo(), new Foo()]) }
}

@CompileStatic
def test() {
    def tor = new Tor<Integer,Foo>()

    tor.foo { it.each { it.bar() } }
}'''
  }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQNAndReferenceToSameUnitClassAndTwoArgs() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo {
    void bar() {
        println 'Haha!'
    }
}

class Tor<D,U> {
    public void foo(@ClosureParams(value=FromString,options=["D,List<U>"]) Closure cl) { cl.call(3, [new Foo(), new Foo()]) }
}

@CompileStatic
def test() {

    def tor = new Tor<Integer,Foo>()

    tor.foo { r, e -> r.times { e.each { it.bar() } } }
}
'''
  }

  void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndPolymorphicSignature() {
    assertScript '''
import groovy.transform.stc.FromString

class Foo {
    void bar() {
        println 'Haha!'
    }
}

class Tor<D,U> {
    public void foo(@ClosureParams(value=FromString,options=["D,List<U>", "D"]) Closure cl) {
        if (cl.maximumNumberOfParameters==2) {
            cl.call(3, [new Foo(), new Foo()])
        } else {
            cl.call(3)
        }
    }
}

@CompileStatic
def test() {
    def tor = new Tor<Integer,Foo>()

    tor.foo { r, e -> r.times { e.each { it.bar() } } }
    tor.foo { it.times { println 'polymorphic' } }
}
'''
  }

  void testTrait1() {
    testHighlighting('''
trait X {
  void foo() {}
}
''')
  }

  void testTrait2() {
    testHighlighting('''
interface A {}
trait X implements A {
  void foo() {}
}
''')
  }

  void testTrait3() {
    testHighlighting('''
class A {}
trait X extends <error descr="Only traits are expected here">A</error> {
  void foo() {}
}
''')
  }

  void testTrait4() {
    testHighlighting('''
trait A {}
trait X extends A {
  void foo() {}
}
''')
  }

  void testTraitExtendsList() {
    testHighlighting('''
trait B extends <error descr="Only traits are expected here">HashMap</error> {}''')
  }

  void testIncIsNotAllowedInTraits() {
    testHighlighting('''
trait C {
  def x = 5
  def foo() {
    <error descr="++ expressions on trait fields/properties are not supported in traits">x++</error>
  }
}
''')
  }

  void testTraitAsAnonymous() {
    testHighlighting('''
trait T {}

new <error descr="Anonymous classes cannot be created from traits">T</error>(){}
''')
  }

  void testAbstractMethodsInTrait() {
    testHighlighting('''
trait T {
  def foo() {}
  def <error descr="Not abstract method should have body">bar</error>()
  abstract baz()
  abstract xyz() <error descr="Abstract methods must not have body">{}</error>
}
''')
  }

  void testTraitImplementsInterfaceMethod() {
    testHighlighting('''
interface A {def foo()}
trait B implements A { def foo() {}}
class C implements B {}
''')
  }

  void 'test not implemented trait method'() {
    testHighlighting('''
trait T {abstract def foo()}
<error descr="Method 'foo' is not implemented">class C implements T</error> {}
''')
  }

  void 'test not implemented interface method with trait in hierarchy'() {
    testHighlighting('''
interface T {def foo()}
trait X implements T {}
<error descr="Method 'foo' is not implemented">class C implements X</error> {}
''')
  }

  void 'test trait methods cannot be protected'(){
    testHighlighting('trait T {<error descr="Trait methods are not allowed to be protected">protected</error> foo(){}}')
  }

  void 'test trait can implement numerous traits'() {
    testHighlighting('''
trait A{}
trait B{}

trait D extends A {}
trait E implements A {}
trait F extends A implements B {}
trait G implements A, B {}

''')
  }

  void 'test trait with generic method no errors'() {
    testHighlighting('''
trait TraitGenericMethod {
    def <X> X bar(X x) { x }
}
class ConcreteClassOfTraitGenericMethod implements TraitGenericMethod {}
''')
  }

  void 'test generic trait with generic method no errors'() {
    testHighlighting('''
trait GenericTraitGenericMethod<X> {
    public <T> T bar(T a) { a }
}
class ConcreteClassGenericTraitGenericMethod implements GenericTraitGenericMethod<String> {}
class GenericClassGenericTraitGenericMethod<Y> implements GenericTraitGenericMethod<Y> {}
''')
  }

  void 'test generic trait no errors'() {
    testHighlighting('''
trait GenericTrait<X> {
    def X bar(X a) { a }
}
class GenericClassGenericTrait<Y> implements GenericTrait<Y> {}
class ConcreteClassGenericTrait implements GenericTrait<String> {}
''')
  }

  void 'test abstract method with default parameters in abstract class'() {
    testHighlighting '''\
abstract class A { abstract foo(x, y = 3) }
<error descr="Method 'foo' is not implemented">class B extends A</error> {}
'''
    testHighlighting '''\
abstract class A { abstract foo(x, y = 3) }
class B extends A { def foo(x,y) {} }
'''
  }

  void 'test abstract method with default parameters in trait'() {
    testHighlighting '''\
trait A { abstract foo(x, y = 3) }
<error descr="Method 'foo' is not implemented">class B implements A</error> {}
'''
    testHighlighting '''\
trait A { abstract foo(x, y = 3) }
class B implements A { def foo(x,y) {} }
'''
  }

  void 'test abstract method with default parameters in trait from Java'() {
    myFixture.addFileToProject 'test.groovy', 'trait T { abstract foo(x, y = 4) }'

    myFixture.configureByText JavaFileType.INSTANCE, '''\
<error descr="Class 'C' must either be declared abstract or implement abstract method 'foo(Object, Object)' in 'T'">class C implements T</error> {}
'''
    myFixture.testHighlighting false, false, false

    myFixture.configureByText JavaFileType.INSTANCE, '''\
<error descr="Class 'C' must either be declared abstract or implement abstract method 'foo(Object, Object)' in 'T'">class C implements T</error> {
  public Object foo() { return null; }
  public Object foo(Object x) { return null; }
}
'''
    myFixture.testHighlighting false, false, false

    myFixture.configureByText JavaFileType.INSTANCE, '''\
class C implements T {
  public Object foo() { return null; }
  public Object foo(Object x) { return null; }
  public Object foo(Object x, Object y) { return null; }
}
'''
    myFixture.testHighlighting false, false, false
  }

  void 'test trait method usages'() {
    testHighlighting '''\
trait T {
    def getFoo() {}
}

class A implements T {}

new A().foo
''', GroovyUnusedDeclarationInspection
  }

  void 'test no exception on super reference in trait without supertypes'() {
    testHighlighting '''\
trait SimpleTrait {
  void foo() {
    super.<warning descr="Cannot resolve symbol 'foo'">foo</warning>()
  }
}
'''
  }

  void 'test private trait method'() {
    testHighlighting '''\
trait T {
    private traitMethod() { 42 }
}
class SomeClass implements T {}

new SomeClass().<warning descr="Access to 'traitMethod' exceeds its access rights">traitMethod</warning>()
''', GroovyAccessibilityInspection
  }

  void 'test spread argument highlight'() {
    testHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
class A {
    def m(int a, String b) {

    }
    def m2(s) {
        m(<error>*[1,""]</error>)
    }
}
'''
  }
}
