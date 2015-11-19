/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

/**
 * @author Max Medvedev
 */
class ResolveWithDelegatesToTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() { null }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myFixture.addClass('''\
package groovy.lang;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface DelegatesTo {
    Class value() default Target.class;
    int strategy() default Closure.OWNER_FIRST;

    String target() default "";

    @Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target({ElementType.PARAMETER})
    public static @interface Target {
        String value() default ""; // optional id
    }
}
''')
  }

  void testShouldChooseMethodFromOwner() {
    assertScript '''
            class Delegate {
                int foo() { 2 }
            }
            class Owner {
                int foo() { 1 }
                int doIt(@DelegatesTo(Delegate) Closure cl) {
                    cl.delegate = new Delegate()
                    cl() as int
                }
                int test() {
                    doIt {
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

  void testShouldChooseMethodFromDelegate() {
    assertScript '''
            class Delegate {
                int foo() { 2 }
            }
            class Owner {
                int foo() { 1 }
                int doIt(@DelegatesTo(strategy=Closure.DELEGATE_FIRST, value=Delegate) Closure cl) {
                    cl.delegate = new Delegate()
                    cl.resolveStrategy = Closure.DELEGATE_FIRST
                    cl() as int
                }
                int test() {
                    doIt {
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

  void testShouldAcceptMethodCall() {
    assertScript '''
            class ExecSpec {
                boolean called = false
                void foo() {
                    called = true
                }
            }

            ExecSpec spec = new ExecSpec()

            void exec(ExecSpec spec, @DelegatesTo(value=ExecSpec, strategy=Closure.DELEGATE_FIRST) Closure param) {
                param.delegate = spec
                param()
            }

            exec(spec) {
                fo<caret>o() // should be recognized because param is annotated with @DelegatesTo(ExecSpec)
            }
            assert spec.isCalled()
        ''', 'ExecSpec'
  }

  void testCallMethodFromOwner() {
    assertScript '''
            class Xml {
                boolean called = false
                void bar() { called = true }
                void foo(@DelegatesTo(Xml)Closure cl) { cl.delegate=this;cl() }
            }
            def mylist = [1]
            def xml = new Xml()
            xml.foo {
             mylist.each { b<caret>ar() }
            }
            assert xml.called
        ''', 'Xml'
  }

  void testEmbeddedWithAndShadowing() {
    assertScript '''
            class A {
                int foo() { 1 }
            }
            class B {
                int foo() { 2 }
            }
            def a = new A()
            def b = new B()
            int result
            a.with {
                b.with {
                    result = fo<caret>o()
                }
            }
            assert result == 2
        ''', 'B'
  }

  void testEmbeddedWithAndShadowing2() {
    assertScript '''
            class A {
                int foo() { 1 }
            }
            class B {
                int foo() { 2 }
            }
            def a = new A()
            def b = new B()
            int result
            b.with {
                a.with {
                    result = fo<caret>o()
                }
            }
            assert result == 1
        ''', 'A'
  }

  void testEmbeddedWithAndShadowing3() {
    assertScript '''
            class A {
                int foo() { 1 }
            }
            class B {
                int foo() { 2 }
            }
            class C {
                int foo() { 3 }
                void test() {
                    def a = new A()
                    def b = new B()
                    int result
                    b.with {
                        a.with {
                            result = fo<caret>o()
                        }
                    }
                    assert result == 1
                }
            }
            new C().test()
        ''', 'A'
  }

  void testEmbeddedWithAndShadowing4() {
    assertScript '''
            class A {
                int foo() { 1 }
            }
            class B {
                int foo() { 2 }
            }
            class C {
                int foo() { 3 }
                void test() {
                    def a = new A()
                    def b = new B()
                    int result
                    b.with {
                        a.with {
                            result = this.f<caret>oo()
                        }
                    }
                    assert result == 3
                }
            }
            new C().test()
        ''', 'C'
  }

  void testShouldDelegateToParameter() {
    assertScript '''
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
    assertScript '''
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

  @SuppressWarnings("GroovyUnusedDeclaration")
  void ignoreTestInConstructor() {
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
''', 'LinkedHashMap'
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

''', 'LinkedHashMap'
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
''', 'LinkedHashMap'
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

''', 'LinkedHashMap'
  }

  void testTarget() {
    assertScript('''
def foo(@DelegatesTo.Target def o, @DelegatesTo Closure c) {}

foo(4) {
  intVal<caret>ue()
}
''', 'Integer')
  }

  void testTarget2() {
    assertScript('''
def foo(@DelegatesTo.Target('t') def o, @DelegatesTo(target = 't') Closure c) {}

foo(4) {
  intVal<caret>ue()
}
''', 'Integer')
  }

  void testGenericTypeIndex() {
    assertScript('''\
public <K, V> void foo(@DelegatesTo.Target Map<K, V> map, @DelegatesTo(genericTypeIndex = 1) Closure c) {}

foo([1:'ab', 2:'cde']) {
  sub<caret>string(1)
}
''', 'String')
  }

  void testGenericTypeIndex1() {
    assertScript('''\
public <K, V> void foo(@DelegatesTo.Target Map<K, V> map, @DelegatesTo(genericTypeIndex = 0) Closure c) {}

foo([1:'ab', 2:'cde']) {
  sub<caret>string(1)
}
''', 'String')
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
''', 'List')
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
    def reference = myFixture.configureByText('_a.groovy', '''\
class X {
    void method() {}
}

void doX(@DelegatesTo(X) Closure c) {
    new X().with(c)
}

doX {
    deleg<caret>ate
}
''').findReferenceAt(myFixture.editor.caretModel.offset)
    assert reference instanceof GrReferenceExpression
    assert reference.type.equalsToText('X')
  }

  void assertScript(String text, String resolvedClass) {
    myFixture.configureByText('_a.groovy', text)

    final ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    final resolved = assertInstanceOf(ref.resolve(), PsiMethod)
    final containingClass = resolved.containingClass.name
    assertEquals(resolvedClass, containingClass)
  }
}
