// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.ThrowingTransformation;
import org.junit.Assert;

/**
 * @author Max Medvedev
 */
public class ResolveWithDelegatesToOnLambdaTest extends GroovyResolveTestCase {
  public void testOwnerFirstMethod() {
    assertScript(
      """
        class Delegate {
          int foo() { 2 }
        }
        class Owner {
          int foo() { 1 }
          int doIt(@DelegatesTo(Delegate) Closure cl) {}
          int test() {
            doIt () -> {
              fo<caret>o() // as the delegation strategy is owner first, should return 1
            }
          }
        }
        """, "Owner");
  }

  public void testDelegateFirstMethod() {
    assertScript(
      """
        class Delegate {
          int foo() { 2 }
        }
        class Owner {
          int foo() { 1 }
          int doIt(@DelegatesTo(strategy=Closure.DELEGATE_FIRST, value=Delegate) Closure cl) {}
          int test() {
            doIt () -> {
              f<caret>oo() // as the delegation strategy is delegate first, should return 2
            }
          }
        }
        """, "Delegate");
  }

  public void testOwnerClosureMethod() {
    assertScript(
      """
        class Xml {
          void bar() {}
          void foo(@DelegatesTo(Xml) Closure cl) {}
        }
        new Xml().foo {              // closure owner which in turn is another closure with a delegate
          [1].each (it) -> { b<caret>ar() }  // the closure
        }
        """, "Xml");
  }

  public void testNestedClosureDelegatesWithSameMethod() {
    getFixture().addFileToProject("classes.groovy",
                                  """
                                    class A {
                                        int foo() { 1 }
                                    }
                                    class B {
                                        int foo() { 2 }
                                    }
                                    """);

    assertScript("""
                   new A().with {
                     new B().with (it) -> {
                       fo<caret>o()
                     }
                   }
                   """, "B");

    assertScript("""
                   new B().with {
                     new A().with (it) -> {
                       fo<caret>o()
                     }
                   }
                   """, "A");
  }

  public void testNestedClosureDelegatesWithSameMethodAndOwner() {
    getFixture().addFileToProject("classes.groovy",
                                  """
                                    class A {
                                        int foo() { 1 }
                                    }
                                    class B {
                                        int foo() { 2 }
                                    }
                                    """);
    assertScript(
      """
        class C {             // closure owner
          int foo() { 3 }
          void test() {
            new B().with (b) -> {    // closure delegate
              new A().with (a) -> {  // the closure
                fo<caret>o()
              }
            }
          }
        }
        """, "A");

    assertScript(
      """
        class C {
          int foo() { 3 }
          void test() {
            new B().with {
              new A().with (a) -> {
                this.f<caret>oo() // explicit qualifier 'this' which is instance of C
              }
            }
          }
        }
        """, "C");
  }

  public void testShouldDelegateToParameter() {
    assertScript(
      """
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
            with(obj, (o) -> { fo<caret>o() })
            assert obj.called
        }
        test()
        """, "Foo");
  }

  public void testShouldDelegateToParameterUsingExplicitId() {
    assertScript(
      """
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
            with(obj, (o) -> { f<caret>oo() })
            assert obj.called
        }
        test()
        """, "Foo");
  }

  public void testNamedArgs() {
    assertScript(
      """
        
        def with(@DelegatesTo.Target Object target, @DelegatesTo(strategy = Closure.DELEGATE_FIRST) Closure arg) {
            for (Closure a : arg) {
                a.delegate = target
                a.resolveStrategy = Closure.DELEGATE_FIRST
                a()
            }
        }
        
        @CompileStatic
        def test() {
            with(a:2, (m) -> {
                print(g<caret>et('a'))
            });
        }
        
        test()
        """, "java.util.LinkedHashMap");
  }

  public void testEllipsisArgs() {
    assertScript(
      """
        def with(@DelegatesTo.Target Object target, @DelegatesTo(strategy = Closure.DELEGATE_FIRST) Closure... arg) {
            for (Closure a : arg) {
                a.delegate = target
                a.resolveStrategy = Closure.DELEGATE_FIRST
                a()
            }
        }
        
        @CompileStatic
        def test() {
            with(a:2, (m) -> {
                print(get('a'))
            }, (m) -> {
                print(g<caret>et('a'))
            } )
        }
        
        test()
        
        """, "java.util.LinkedHashMap");
  }

  public void testShouldChooseMethodFromOwnerInJava() {
    myFixture.configureByText("Abc.java", """
      import groovy.lang.Closure;
      import groovy.lang.DelegatesTo;
      
      class Abc {
          static int doIt(@DelegatesTo(Delegate.class) Closure cl) {
              cl.delegate = new Delegate()
              cl() as int
          }
      }
      """);
    assertScript(
      """
        class Delegate {
            int foo() { 2 }
        }
        class Owner {
            int foo() { 1 }
        
            int test() {
                Abc.doIt () -> {
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
        """, "Owner");
  }

  public void testShouldChooseMethodFromDelegateInJava() {
    myFixture.configureByText("Abc.java",
                              """
                                import groovy.lang.Closure;
                                import groovy.lang.DelegatesTo;
                                
                                class Abc {
                                  static int doIt(@DelegatesTo(strategy=Closure.DELEGATE_FIRST, value=Delegate.class) Closure cl) {
                                      cl.delegate = new Delegate()
                                      cl.resolveStrategy = Closure.DELEGATE_FIRST
                                      cl() as int
                                  }
                                }
                                """);

    assertScript(
      """
        class Delegate {
            int foo() { 2 }
        }
        class Owner {
            int foo() { 1 }
            int test() {
                Abc.doIt () -> {
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
        """, "Delegate");
  }

  public void testShouldAcceptMethodCallInJava() {
    myFixture.configureByText("Abc.java",
                              """
                                import groovy.lang.Closure;
                                import groovy.lang.DelegatesTo;
                                
                                class Abc {
                                  static void exec(ExecSpec spec, @DelegatesTo(value=ExecSpec.class, strategy=Closure.DELEGATE_FIRST) Closure param) {
                                      param.delegate = spec
                                      param()
                                  }
                                }
                                """);

    assertScript(
      """
        class ExecSpec {
            boolean called = false
            void foo() {
                called = true
            }
        }
        
        ExecSpec spec = new ExecSpec()
        
        Abc.exec(spec, ()->{
            fo<caret>o() // should be recognized because param is annotated with @DelegatesTo(ExecSpec)
        })
        assert spec.isCalled()
        """, "ExecSpec");
  }

  public void testCallMethodFromOwnerInJava() {
    myFixture.configureByText("Abc.java",
                              """
                                import groovy.lang.Closure;
                                import groovy.lang.DelegatesTo;
                                
                                class Xml {
                                  boolean called = false;
                                  void bar() { called = true; }
                                  void foo(@DelegatesTo(Xml.class)Closure cl) { cl.delegate=this;cl(); }
                                }
                                """);

    assertScript(
      """
        def mylist = [1]
        def xml = new Xml()
        xml.foo {
         mylist.each (it) -> { b<caret>ar() }
        }
        assert xml.called
        """, "Xml");
  }

  public void testShouldDelegateToParameterInJava() {
    myFixture.configureByText("Abc.java", """
      import groovy.lang.Closure;
      import groovy.lang.DelegatesTo;
      
      class Abc {
        static void with(@DelegatesTo.Target Object target, @DelegatesTo Closure arg) {
            arg.delegate = target;
            arg();
        }
      }
      """);
    assertScript(
      """
        class Foo {
            boolean called = false
            def foo() { called = true }
        }
        
        def test() {
            def obj = new Foo()
            Abc.with(obj, (o)->{ fo<caret>o() })
            assert obj.called
        }
        test()
        """, "Foo");
  }

  public void testShouldDelegateToParameterUsingExplicitIdInJava() {
    myFixture.configureByText("Abc.java",
                              """
                                import groovy.lang.Closure;
                                import groovy.lang.DelegatesTo;
                                
                                class Abc{
                                  static void with(@DelegatesTo.Target("target") Object target, @DelegatesTo(target="target") Closure arg) {
                                      arg.delegate = target;
                                      arg();
                                  }
                                }
                                """);

    assertScript(
      """
        class Foo {
            boolean called = false
            def foo() { called = true }
        }
        
        def test() {
            def obj = new Foo()
            Abc.with(obj, (o)->{ f<caret>oo() })
            assert obj.called
        }
        test()
        """, "Foo");
  }

  @SuppressWarnings("GroovyUnusedDeclaration")
  public void ignoreTestInConstructorInJava() {
    myFixture.configureByText("Abc.java",
                              """
                                import groovy.lang.Closure;
                                import groovy.lang.DelegatesTo;
                                
                                class Abc {
                                  Abc(@DelegatesTo(Foo.class) Closure cl) {
                                  }
                                }
                                """);

    assertScript(
      """
        class Foo {
          def foo() {}
        }
        
        new Abc(() -> {fo<caret>o()})
        """, "Foo");
  }

  public void testNamedArgsInJava() {
    myFixture.configureByText("Abc.java",
                              """
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
                                """);

    assertScript(
      """
        @CompileStatic
        def test() {
            Abc.with(a:2, () -> {
                print(g<caret>et('a'))
            })
        }
        
        test()
        """, "java.util.LinkedHashMap");
  }

  public void testEllipsisArgsInJava() {
    myFixture.configureByText("Abc.java", """
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
      """);

    assertScript(
      """
        @CompileStatic
        def test() {
            Abc.with(a:2, () -> {
                print(get('a'))
            }, () -> {
                print(g<caret>et('a'))
            } )
        }
        
        test()
        """, "java.util.LinkedHashMap");
  }

  public void testTarget() {
    assertScript("""
                   def foo(@DelegatesTo.Target def o, @DelegatesTo Closure c) {}
                   
                   foo(4, () -> {
                     intVal<caret>ue()
                   })
                   """, "java.lang.Integer");
  }

  public void testTarget2() {
    assertScript("""
                   def foo(@DelegatesTo.Target('t') def o, @DelegatesTo(target = 't') Closure c) {}
                   
                   foo(4, ()->{
                     intVal<caret>ue()
                   })
                   """, "java.lang.Integer");
  }

  public void testGenericTypeIndex() {
    assertScript("""
                   public <K, V> void foo(@DelegatesTo.Target Map<K, V> map, @DelegatesTo(genericTypeIndex = 1) Closure c) {}
                   
                   foo([1:'ab', 2:'cde'],()-> {
                     sub<caret>string(1)
                   })
                   """, "java.lang.String");
  }

  public void testGenericTypeIndex1() {
    assertScript("""
                   public <K, V> void foo(@DelegatesTo.Target Map<K, V> map, @DelegatesTo(genericTypeIndex = 0) Closure c) {}
                   
                   foo([1:'ab', 2:'cde'],()-> {
                     int<caret>Value(1)
                   })
                   """, "java.lang.Integer");
  }

  public void testDelegateAndDelegatesTo() {
    assertScript("""
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
                       wrapper.withList () -> {
                           ad<caret>d(1)
                       }
                       assert wrapper.list == [1]
                   }
                   """, "java.util.List");
  }

  public void testClassTest() {
    assertScript("""
                   class DelegatesToTest {
                       void create(@DelegatesTo.Target Class type, @DelegatesTo(genericTypeIndex = 0, strategy = Closure.OWNER_FIRST) Closure closure) {}
                   
                   
                       void doit() {
                           create(Person, () -> {
                               a<caret>ge = 30
                           })
                       }
                   }
                   
                   class Person {
                       int age
                   }
                   """, "Person");
  }

  public void testAccessViaDelegate() {
    assertScript("""
                   class X {
                       void method() {}
                   }
                   
                   void doX(@DelegatesTo(X) Closure c) {
                       new X().with(c)
                   }
                   
                   doX () -> {
                       delegate.me<caret>thod()
                   }
                   """, "X");
  }

  public void testDelegateType() {
    GrReferenceExpression ref = configureByText("_.groovy", """
      class X {
          void method() {}
      }
      
      void doX(@DelegatesTo(X) Closure c) {
          new X().with(c)
      }
      
      doX () -> {
          deleg<caret>ate
      }
      """, GrReferenceExpression.class);
    Assert.assertTrue(ref.getType().equalsToText("X"));
  }

  public void testDelegateWithinImplicitCall() {
    assertScript(
      """
        class A {
            def call(@DelegatesTo(Boo) Closure c) {}
        }
        
        class Boo {
            def foo() {}
        }
        
        def a = new A()
        a () -> {
            f<caret>oo()
        }
        """, "Boo");
  }

  public void testDelegateWithinConstructorArgument() {
    assertScript(
      """
        class A {
            A(@DelegatesTo(Boo) Closure c) {}
        }
        
        class Boo {
            def foo() {}
        }
        
        new A(() -> {
            fo<caret>o()
        })
        """, "Boo");
  }

  public void testDelegatesToType() {
    assertScript(
      """
        def foo(@DelegatesTo(type = 'String') Closure datesProcessor) {}
        
        foo () -> {
          toUppe<caret>rCase()
        }
        """, "java.lang.String");
  }

  public void testDelegatesToTypeWithGenerics() {
    assertScript(
      """
        def foo(@DelegatesTo(type = 'List<Date>') Closure datesProcessor) {}
        foo () -> {
          get(0).aft<caret>er(null)
        }
        """, "java.util.Date");
  }

  public void testDelegateToInTraitMethod() {
    assertScript(
      """
        trait T {
          def foo(@DelegatesTo(String) Closure c) {}
        }
        class A implements T {}
        new A().foo () -> {
          toUpp<caret>erCase()
        }
        """, "java.lang.String");
    assertScript(
      """
        trait T {
          def foo(@DelegatesTo(type = 'List<String>') Closure c) {}
        }
        class A implements T {}
        new A().foo () -> {
          get(0).toUpp<caret>erCase()
        }
        """, "java.lang.String");
  }

  public void testDelegateOnlyLocalVariables() {
    getFixture().addFileToProject("Methods.groovy", """
      class Methods {
        static m1(@DelegatesTo(value = String, strategy = Closure.DELEGATE_ONLY) Closure c) {}
      }
      """);
    ThrowingTransformation.disableTransformations(getTestRootDisposable());

    // resolve to outer closure parameter
    resolveByText(
      """
        def c = { String s1 ->
          Methods.m1 () -> { s<caret>1 + toUpperCase() }
        }
        """, GrParameter.class);

    // resolve to outer lambda parameter
    resolveByText(
      """
        def c = () -> { String s1 ->
          Methods.m1 () -> { s<caret>1 + toUpperCase() }
        }
        """, GrParameter.class);

    //    // resolve to outer closure local variable
    //    resolveByText('''\
    //def c = { String s1 ->
    //  def s2 = "123"
    //  Methods.m1 () -> { s<caret>2 + toUpperCase() }
    //}
    //''', GrVariable).with {
    //      assert !(it instanceof GrField) && !(it instanceof GrParameter)
    //    }

    // resolve to outer closure local variable
    GrVariable variable = (GrVariable)resolveByText(
      """
        def c = () -> { String s1 ->
          def s2 = "123"
          Methods.m1 () -> { s<caret>2 + toUpperCase() }
        }
        """);
    Assert.assertTrue(!(variable instanceof GrField) && !(variable instanceof GrParameter));


    // resolve to outer method parameter
    resolveByText(
      """
        def m(String s1) {
          Methods.m1 () -> {s<caret>1 + toUpperCase() }
        }
        """, GrParameter.class);

    // resolve to outer method local variable
    variable = (GrVariable)resolveByText("""
                                           def m(String s1) {
                                             def s2 = "123"
                                             Methods.m1 () -> { s1 + s<caret>2 + toUpperCase() }
                                           }
                                           """);
    Assert.assertTrue(!(variable instanceof GrField) && !(variable instanceof GrParameter));
  }

  public void testDelegateOnlyClasses() {
    getFixture().addFileToProject("Methods.groovy",
                                  """
                                    class Methods {
                                      static m1(@DelegatesTo(value = String, strategy = Closure.DELEGATE_ONLY) Closure c) {}
                                    }
                                    """);

    PsiElement element = resolveByText(
      """
        Methods.m1 () -> {
          <caret>String
        }
        """);
    Assert.assertTrue(element instanceof PsiClass);
  }

  public void testImplicitCall() {
    resolveByText(
      """
        class D { def foo() { 42 } }
        
        class C {
          def call(@DelegatesTo(D) Closure cl) {
            cl.delegate = new D()
            cl()
          }
        }
        
        def c = new C()
        c () -> {
          <caret>foo()
        }
        """, GrMethod.class);
  }

  private void assertScript(String text, String resolvedClass) {
    PsiMethod resolved = resolveByText(text, PsiMethod.class);
    final String containingClass = resolved.getContainingClass().getQualifiedName();
    Assert.assertEquals(resolvedClass, containingClass);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;
}
