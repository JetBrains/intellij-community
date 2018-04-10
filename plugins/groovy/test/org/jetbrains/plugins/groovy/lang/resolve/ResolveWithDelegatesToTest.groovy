// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter

import static org.jetbrains.plugins.groovy.util.ThrowingTransformation.disableTransformations

/**
 * @author Max Medvedev
 */
@CompileStatic
class ResolveWithDelegatesToTest extends GroovyResolveTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test owner first method'() {
    assertScript '''\
class Delegate {
  int foo() { 2 }
}
class Owner {
  int foo() { 1 }
  int doIt(@DelegatesTo(Delegate) Closure cl) {}
  int test() {
    doIt {
      fo<caret>o() // as the delegation strategy is owner first, should return 1
    }
  }
}
''', 'Owner'
  }

  void 'test delegate first method'() {
    assertScript '''\
class Delegate {
  int foo() { 2 }
}
class Owner {
  int foo() { 1 }
  int doIt(@DelegatesTo(strategy=Closure.DELEGATE_FIRST, value=Delegate) Closure cl) {}
  int test() {
    doIt {
      f<caret>oo() // as the delegation strategy is delegate first, should return 2
    }
  }
}
''', 'Delegate'
  }

  void 'test owner closure method'() {
    assertScript '''\
class Xml {
  void bar() {}
  void foo(@DelegatesTo(Xml) Closure cl) {}
}
new Xml().foo {              // closure owner which in turn is another closure with a delegate
  [1].each { b<caret>ar() }  // the closure
}
''', 'Xml'
  }

  void 'test nested closure delegates with same method'() {
    fixture.addFileToProject 'classes.groovy', '''\
class A {
    int foo() { 1 }
}
class B {
    int foo() { 2 }
}
'''

    assertScript '''\
new A().with {
  new B().with {
    fo<caret>o()
  }
}
''', 'B'

    assertScript '''\
new B().with {
  new A().with {
    fo<caret>o()
  }
}
''', 'A'
  }

  void 'test nested closure delegates with same method and owner'() {
    fixture.addFileToProject 'classes.groovy', '''\
class A {
    int foo() { 1 }
}
class B {
    int foo() { 2 }
}
'''
    assertScript '''\
class C {             // closure owner
  int foo() { 3 }
  void test() {
    new B().with {    // closure delegate
      new A().with {  // the closure
        fo<caret>o()
      }
    }
  }
}
''', 'A'

    assertScript '''\
class C {
  int foo() { 3 }
  void test() {
    new B().with {
      new A().with {
        this.f<caret>oo() // explicit qualifier 'this' which is instance of C
      }
    }
  }
}
''', 'C'
  }

  void testShouldDelegateToParameter() {
    assertScript '''\
class Foo {
    boolean called = false
    def foo() { called = true }
}

def with(@DelegatesTo.Target Object target, @DelegatesTo Closure arg) {
    arg.delegate = target
    arg()
}

def test() {
    def obj = new Foo()
    with(obj) { fo<caret>o() }
    assert obj.called
}
test()
''', 'Foo'
  }

  void testShouldDelegateToParameterUsingExplicitId() {
    assertScript '''\
class Foo {
    boolean called = false
    def foo() { called = true }
}

def with(@DelegatesTo.Target('target') Object target, @DelegatesTo(target='target') Closure arg) {
    arg.delegate = target
    arg()
}

def test() {
    def obj = new Foo()
    with(obj) { f<caret>oo() }
    assert obj.called
}
test()
''', 'Foo'
  }

  void testInConstructor() {
    assertScript '''
        class Foo {
          def foo() {}
        }

        class Abc {
          def Abc(@DelegatesTo(Foo) Closure cl) {
          }
        }

        new Abc({fo<caret>o()})
''', 'Foo'
  }

  void testNamedArgs() {
    assertScript '''
def with(@DelegatesTo.Target Object target, @DelegatesTo(strategy = Closure.DELEGATE_FIRST) Closure arg) {
    for (Closure a : arg) {
        a.delegate = target
        a.resolveStrategy = Closure.DELEGATE_FIRST
        a()
    }
}

@CompileStatic
def test() {
    with(a:2) {
        print(g<caret>et('a'))
    }
}

test()
''', 'java.util.LinkedHashMap'
  }

  void testEllipsisArgs() {
    assertScript '''
def with(@DelegatesTo.Target Object target, @DelegatesTo(strategy = Closure.DELEGATE_FIRST) Closure... arg) {
    for (Closure a : arg) {
        a.delegate = target
        a.resolveStrategy = Closure.DELEGATE_FIRST
        a()
    }
}

@CompileStatic
def test() {
    with(a:2, {
        print(get('a'))
    }, {
        print(g<caret>et('a'))
    } )
}

test()

''', 'java.util.LinkedHashMap'
  }

  void testShouldChooseMethodFromOwnerInJava() {
    myFixture.configureByText("Abc.java", '''\
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

            class Abc {
                static int doIt(@DelegatesTo(Delegate.class) Closure cl) {
                    cl.delegate = new Delegate()
                    cl() as int
                }
            }
''')
    assertScript '''
            class Delegate {
                int foo() { 2 }
            }
            class Owner {
                int foo() { 1 }

                int test() {
                    Abc.doIt {
                        @ASTTest(phase=INSTRUCTION_SELECTION, value={
                            node = node.rightExpression
                            def target = node.getNodeMetaData(DIRECT_METHOD_CALL_TARGET)
                            assert target != null
                            assert target.declaringClass.name == 'Owner'
                        })
                        def x = fo<caret>o() // as the delegation strategy is owner first, should return 1
                        x
                    }
                }
            }
            def o = new Owner()
            assert o.test() == 1
        ''', 'Owner'
  }

  void testShouldChooseMethodFromDelegateInJava() {
    myFixture.configureByText("Abc.java", '''\
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

class Abc {
                static int doIt(@DelegatesTo(strategy=Closure.DELEGATE_FIRST, value=Delegate.class) Closure cl) {
                    cl.delegate = new Delegate()
                    cl.resolveStrategy = Closure.DELEGATE_FIRST
                    cl() as int
                }

}''')

    assertScript '''
            class Delegate {
                int foo() { 2 }
            }
            class Owner {
                int foo() { 1 }
                int test() {
                    Abc.doIt {
                        @ASTTest(phase=INSTRUCTION_SELECTION, value={
                            node = node.rightExpression
                            def target = node.getNodeMetaData(DIRECT_METHOD_CALL_TARGET)
                            assert target != null
                            assert target.declaringClass.name == 'Delegate'
                        })
                        def x = f<caret>oo() // as the delegation strategy is delegate first, should return 2
                        x
                    }
                }
            }
            def o = new Owner()
            assert o.test() == 2
        ''', 'Delegate'
  }

  void testShouldAcceptMethodCallInJava() {
    myFixture.configureByText("Abc.java", '''\
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

class Abc {
            static void exec(ExecSpec spec, @DelegatesTo(value=ExecSpec.class, strategy=Closure.DELEGATE_FIRST) Closure param) {
                param.delegate = spec
                param()
            }
}''')

    assertScript '''
            class ExecSpec {
                boolean called = false
                void foo() {
                    called = true
                }
            }

            ExecSpec spec = new ExecSpec()

            Abc.exec(spec) {
                fo<caret>o() // should be recognized because param is annotated with @DelegatesTo(ExecSpec)
            }
            assert spec.isCalled()
        ''', 'ExecSpec'
  }

  void testCallMethodFromOwnerInJava() {
    myFixture.configureByText("Abc.java", '''
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

class Xml {
                boolean called = false;
                void bar() { called = true; }
                void foo(@DelegatesTo(Xml.class)Closure cl) { cl.delegate=this;cl(); }
}
''')

    assertScript '''
            def mylist = [1]
            def xml = new Xml()
            xml.foo {
             mylist.each { b<caret>ar() }
            }
            assert xml.called
        ''', 'Xml'
  }

  void testShouldDelegateToParameterInJava() {
    myFixture.configureByText('Abc.java', '''\
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

class Abc {
        static void with(@DelegatesTo.Target Object target, @DelegatesTo Closure arg) {
            arg.delegate = target;
            arg();
        }

}
''')
    assertScript '''
        class Foo {
            boolean called = false
            def foo() { called = true }
        }

        def test() {
            def obj = new Foo()
            Abc.with(obj) { fo<caret>o() }
            assert obj.called
        }
        test()
        ''', 'Foo'
  }

  void testShouldDelegateToParameterUsingExplicitIdInJava() {
    myFixture.configureByText("Abc.java", '''\
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

class Abc{
        static void with(@DelegatesTo.Target("target") Object target, @DelegatesTo(target="target") Closure arg) {
            arg.delegate = target;
            arg();
        }
}''')

    assertScript '''
        class Foo {
            boolean called = false
            def foo() { called = true }
        }

        def test() {
            def obj = new Foo()
            Abc.with(obj) { f<caret>oo() }
            assert obj.called
        }
        test()
        ''', 'Foo'
  }

  @SuppressWarnings("GroovyUnusedDeclaration")
  void ignoreTestInConstructorInJava() {
    myFixture.configureByText("Abc.java", '''
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

class Abc {
          Abc(@DelegatesTo(Foo.class) Closure cl) {
          }
}
''')

    assertScript '''
        class Foo {
          def foo() {}
        }

        new Abc({fo<caret>o()})
''', 'Foo'
  }

  void testNamedArgsInJava() {
    myFixture.configureByText("Abc.java", '''\
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

class Abc {
  static void with(@DelegatesTo.Target Object target, @DelegatesTo(strategy = Closure.DELEGATE_FIRST) Closure arg) {
    for (Closure a : arg) {
        a.delegate = target
        a.resolveStrategy = Closure.DELEGATE_FIRST
        a()
    }
  }
}
''')

    assertScript '''
@CompileStatic
def test() {
    Abc.with(a:2) {
        print(g<caret>et('a'))
    }
}

test()
''', 'java.util.LinkedHashMap'
  }

  void testEllipsisArgsInJava() {
    myFixture.configureByText("Abc.java", '''\
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

class Abc {
    static void with(@DelegatesTo.Target Object target, @DelegatesTo(strategy = Closure.DELEGATE_FIRST) Closure... arg) {
        for (Closure a : arg) {
            a.delegate = target
            a.resolveStrategy = Closure.DELEGATE_FIRST
            a()
        }
    }
}
''')

    assertScript '''
@CompileStatic
def test() {
    Abc.with(a:2, {
        print(get('a'))
    }, {
        print(g<caret>et('a'))
    } )
}

test()

''', 'java.util.LinkedHashMap'
  }

  void testTarget() {
    assertScript('''
def foo(@DelegatesTo.Target def o, @DelegatesTo Closure c) {}

foo(4) {
  intVal<caret>ue()
}
''', 'java.lang.Integer')
  }

  void testTarget2() {
    assertScript('''
def foo(@DelegatesTo.Target('t') def o, @DelegatesTo(target = 't') Closure c) {}

foo(4) {
  intVal<caret>ue()
}
''', 'java.lang.Integer')
  }

  void testGenericTypeIndex() {
    assertScript('''\
public <K, V> void foo(@DelegatesTo.Target Map<K, V> map, @DelegatesTo(genericTypeIndex = 1) Closure c) {}

foo([1:'ab', 2:'cde']) {
  sub<caret>string(1)
}
''', 'java.lang.String')
  }

  void testGenericTypeIndex1() {
    assertScript('''\
public <K, V> void foo(@DelegatesTo.Target Map<K, V> map, @DelegatesTo(genericTypeIndex = 0) Closure c) {}

foo([1:'ab', 2:'cde']) {
  int<caret>Value(1)
}
''', 'java.lang.Integer')
  }

  void testDelegateAndDelegatesTo() {
    assertScript('''
import groovy.transform.CompileStatic

class Subject {
    List list = []

    void withList(@DelegatesTo(List) Closure<?> closure) {
        list.with(closure)
    }
}

class Wrapper {
    @Delegate(parameterAnnotations = true)
    Subject subject = new Subject()
}

@CompileStatic
def staticOnWrapper() {
    def wrapper = new Wrapper()
    wrapper.withList {
        ad<caret>d(1)
    }
    assert wrapper.list == [1]
}
''', 'java.util.List')
  }

  void testClassTest() {
    assertScript('''
class DelegatesToTest {
    void create(@DelegatesTo.Target Class type, @DelegatesTo(genericTypeIndex = 0, strategy = Closure.OWNER_FIRST) Closure closure) {}


    void doit() {
        create(Person) {
            a<caret>ge = 30 // IDEA 12.1.6 can resolve this property, 13.1.3 can't
        }
    }
}

class Person {
    int age
}
''', 'Person')
  }

  void 'test access via delegate'() {
    assertScript '''\
class X {
    void method() {}
}

void doX(@DelegatesTo(X) Closure c) {
    new X().with(c)
}

doX {
    delegate.me<caret>thod()
}
''', 'X'
  }

  void 'test delegate type'() {
    def ref = configureByText('_.groovy', '''\
class X {
    void method() {}
}

void doX(@DelegatesTo(X) Closure c) {
    new X().with(c)
}

doX {
    deleg<caret>ate
}
''', GrReferenceExpression)
    assert ref.type.equalsToText('X')
  }

  void 'test delegate within implicit call()'() {
    assertScript '''\
class A {
    def call(@DelegatesTo(Boo) Closure c) {}
}

class Boo {
    def foo() {}
}

def a = new A()
a {
    f<caret>oo()
}
''', 'Boo'
  }

  void 'test delegate within constructor argument'() {
    assertScript '''\
class A {
    A(@DelegatesTo(Boo) Closure c) {}
}

class Boo {
    def foo() {}
}

new A({
    fo<caret>o()
})
''', 'Boo'
  }

  void 'test @DelegatesTo type'() {
    assertScript '''\
def foo(@DelegatesTo(type = 'String') Closure datesProcessor) {}

foo {
  toUppe<caret>rCase()
}
''', 'java.lang.String'
  }

  void 'test @DelegatesTo type with generics'() {
    assertScript '''\
def foo(@DelegatesTo(type = 'List<Date>') Closure datesProcessor) {}
foo {
  get(0).aft<caret>er(null)
}
''', 'java.util.Date'
  }

  void 'test @DelegateTo in trait method'() {
    assertScript '''\
trait T {
  def foo(@DelegatesTo(String) Closure c) {}
}
class A implements T {}
new A().foo {
  toUpp<caret>erCase()
}
''', 'java.lang.String'
    assertScript '''\
trait T {
  def foo(@DelegatesTo(type = 'List<String>') Closure c) {}
}
class A implements T {}
new A().foo {
  get(0).toUpp<caret>erCase()
}
''', 'java.lang.String'
  }

  void 'test delegate only local variables'() {
    fixture.addFileToProject 'Methods.groovy', '''\
class Methods {
  static m1(@DelegatesTo(value = String, strategy = Closure.DELEGATE_ONLY) Closure c) {}
}
'''
    disableTransformations testRootDisposable

    // resolve to outer closure parameter
    resolveByText '''\
def c = { String s1 ->
  Methods.m1 { s<caret>1 + toUpperCase() }
}
''', GrParameter

    // resolve to outer closure local variable
    resolveByText('''\
def c = { String s1 ->
  def s2 = "123"
  Methods.m1 { s<caret>2 + toUpperCase() }
}
''', GrVariable).with {
      assert !(it instanceof GrField) && !(it instanceof GrParameter)
    }

    // resolve to outer method parameter
    resolveByText '''\
def m(String s1) {
  Methods.m1 {s<caret>1 + toUpperCase() }
}
''', GrParameter

    // resolve to outer method local variable
    resolveByText('''\
def m(String s1) {
  def s2 = "123"
  Methods.m1 { s1 + s<caret>2 + toUpperCase() }
}
''', GrVariable).with {
      assert !(it instanceof GrField) && !(it instanceof GrParameter)
    }
  }

  void 'test delegate only classes'() {
    fixture.addFileToProject 'Methods.groovy', '''\
class Methods {
  static m1(@DelegatesTo(value = String, strategy = Closure.DELEGATE_ONLY) Closure c) {}
}
'''

    resolveByText('''\
Methods.m1 {
  <caret>String
}
''').with {
      assert it instanceof PsiClass
    }
  }

  private void assertScript(String text, String resolvedClass) {
    def resolved = resolveByText(text, PsiMethod)
    final containingClass = resolved.containingClass.qualifiedName
    assertEquals(resolvedClass, containingClass)
  }
}
