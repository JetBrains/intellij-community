// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;

/**
 * Created by Max Medvedev on 27/02/14
 */
public class Gr23HighlightingTest extends GrHighlightingTestBase {
  @Override
  public InspectionProfileEntry[] getCustomInspections() {
    return new LocalInspectionTool[]{new GroovyAssignabilityCheckInspection(), new GrUnresolvedAccessInspection()};
  }

  public void assertScript(String text) {
    doTestHighlighting("import groovy.transform.CompileStatic\nimport groovy.transform.stc.ClosureParams\n" + text);
  }

  public void shouldFailWithMessages(String text) {
    assertScript(text);
  }

  public void testInferenceOnNonExtensionMethod() {
    assertScript("""
                   import groovy.transform.stc.FirstParam

                   public <T> T foo(T arg, @ClosureParams(FirstParam) Closure c) { c.call(arg) }

                   @CompileStatic
                   def test() {
                       assert foo('a') { it.toUpperCase() } == 'A'
                   }
                   """);
  }

  public void testFromStringWithSimpleType() {
    assertScript("""
                   import groovy.transform.stc.FromString

                   void foo(@ClosureParams(value=FromString,options="java.lang.String") Closure cl) { cl.call('foo') }

                   @CompileStatic
                   def test() {
                       foo { String str -> println str.toUpperCase()}
                   }
                   """);
    shouldFailWithMessages("""
                             import groovy.transform.stc.FromString

                             void foo(@ClosureParams(value=FromString,options="java.lang.String") Closure cl) { cl.call('foo') }

                             @CompileStatic
                             def test() {
                                 foo { <error descr="Expected 'java.lang.String', found 'java.util.Date'">Date</error> str -> println str}
                             }
                             """);
  }

  public void testFromStringWithGenericType() {
    assertScript("""
                   import groovy.transform.stc.FromString

                   void foo(@ClosureParams(value=FromString,options="java.util.List<java.lang.String>") Closure cl) { cl.call(['foo']) }

                   @CompileStatic
                   def test() {
                       foo { List<String> str -> str.each { println it.toUpperCase() } }
                   }
                   """);
    shouldFailWithMessages("""
                             import groovy.transform.stc.FromString

                             void foo(@ClosureParams(value=FromString,options="java.util.List<java.lang.String>") Closure cl) { cl.call(['foo']) }

                             @CompileStatic
                             def test() {
                                 foo { <error descr="Expected 'java.util.List<java.lang.String>', found 'java.util.List<java.util.Date>'">List<Date></error> d -> d.each { println it } }
                             }
                             """);
  }

  public void testFromStringWithDirectGenericPlaceholder() {
    assertScript("""
                   import groovy.transform.stc.FromString

                   public <T> void foo(T t, @ClosureParams(value=FromString,options="T") Closure cl) { cl.call(t) }

                   @CompileStatic
                   def test() {
                       foo('hey') { println it.toUpperCase() }
                   }
                   """);
  }

  public void testFromStringWithGenericPlaceholder() {
    assertScript("""
                   import groovy.transform.stc.FromString

                   public <T> void foo(T t, @ClosureParams(value=FromString,options="java.util.List<T>") Closure cl) { cl.call([t,t]) }

                   @CompileStatic
                   def test() {
                       foo('hey') { List<String> str -> str.each { println it.toUpperCase() } }
                   }
                   """);
  }

  public void testFromStringWithGenericPlaceholderFromClass() {
    assertScript("""
                   import groovy.transform.stc.FromString

                   class Foo<T> {
                       public void foo(@ClosureParams(value=FromString,options="java.util.List<T>") Closure cl) { cl.call(['hey','ya']) }
                   }

                   @CompileStatic
                   def test() {
                       def foo = new Foo<String>()

                       foo.foo { List<String> str -> str.each { println it.toUpperCase() } }
                   }""");
  }

  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenerics() {
    assertScript("""
                   import groovy.transform.stc.FromString

                   class Foo<T,U> {
                       public void foo(@ClosureParams(value=FromString,options="java.util.List<U>") Closure cl) { cl.call(['hey','ya']) }
                   }

                   @CompileStatic
                   def test() {
                       def foo = new Foo<Integer,String>()

                       foo.foo { List<String> str -> str.each { println it.toUpperCase() } }
                   }""");
  }

  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignature() {
    assertScript("""
                   import groovy.transform.stc.FromString

                   class Foo<T,U> {
                       public void foo(@ClosureParams(value=FromString,options="java.util.List<U>") Closure cl) { cl.call(['hey','ya']) }
                   }

                   @CompileStatic
                   def test() {
                       def foo = new Foo<Integer,String>()

                       foo.foo { it.each { println it.toUpperCase() } }
                   }""");
  }

  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQN() {
    assertScript("""
                   import groovy.transform.stc.FromString

                   class Foo<T,U> {
                       public void foo(@ClosureParams(value=FromString,options="List<U>") Closure cl) { cl.call(['hey','ya']) }
                   }

                   @CompileStatic
                   def test() {
                       def foo = new Foo<Integer,String>()

                       foo.foo { it.each { println it.toUpperCase() } }
                   }""");
  }

  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQNAndReferenceToSameUnitClass() {
    assertScript("""
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
                   }""");
  }

  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndNoExplicitSignatureAndNoFQNAndReferenceToSameUnitClassAndTwoArgs() {
    assertScript("""
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
                   """);
  }

  public void testFromStringWithGenericPlaceholderFromClassWithTwoGenericsAndPolymorphicSignature() {
    assertScript("""
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
                   """);
  }

  public void testTrait1() {
    doTestHighlighting("""
                         trait X {
                           void foo() {}
                         }
                         """);
  }

  public void testTrait2() {
    doTestHighlighting("""
                         interface A {}
                         trait X implements A {
                           void foo() {}
                         }
                         """);
  }

  public void testTrait3() {
    doTestHighlighting("""
                         class A {}
                         trait X extends <error descr="Only traits are expected here">A</error> {
                           void foo() {}
                         }
                         """);
  }

  public void testTrait4() {
    doTestHighlighting("""
                         trait A {}
                         trait X extends A {
                           void foo() {}
                         }
                         """);
  }

  public void testTraitExtendsList() {
    doTestHighlighting("trait B extends <error descr=\"Only traits are expected here\">HashMap</error> {}");
  }

  public void testIncIsNotAllowedInTraits() {
    doTestHighlighting("""
                         trait C {
                           def x = 5
                           def foo() {
                             <error descr="++ expressions on trait fields/properties are not supported in traits">x++</error>
                           }
                         }
                         """);
  }

  public void testTraitAsAnonymous() {
    doTestHighlighting("""
                         trait T {}

                         new <error descr="Anonymous classes from traits are available since Groovy 2.5.2">T</error>(){}
                         """);
  }

  public void testAbstractMethodsInTrait() {
    doTestHighlighting("""
                         trait T {
                           def foo() {}
                           def <error descr="Not abstract method should have body">bar</error>()
                           abstract baz()
                           abstract xyz() <error descr="Abstract methods must not have body">{}</error>
                         }
                         """);
  }

  public void testTraitImplementsInterfaceMethod() {
    doTestHighlighting("""
                         interface A {def foo()}
                         trait B implements A { def foo() {}}
                         class C implements B {}
                         """);
  }

  public void testNotImplementedTraitMethod() {
    doTestHighlighting("""
                         trait T {abstract def foo()}
                         <error descr="Method 'foo' is not implemented">class C implements T</error> {}
                         """);
  }

  public void testNotImplementedInterfaceMethodWithTraitInHierarchy() {
    doTestHighlighting("""
                         interface T {def foo()}
                         trait X implements T {}
                         <error descr="Method 'foo' is not implemented">class C implements X</error> {}
                         """);
  }

  public void testTraitMethodsCannotBeProtected() {
    doTestHighlighting("trait T {<error descr=\"Trait methods are not allowed to be protected\">protected</error> foo(){}}");
  }

  public void testTraitCanImplementNumerousTraits() {
    doTestHighlighting("""
                         trait A{}
                         trait B{}

                         trait D extends A {}
                         trait E implements A {}
                         trait F extends A implements B {}
                         trait G implements A, B {}
                         """);
  }

  public void testTraitWithGenericMethodNoErrors() {
    doTestHighlighting("""
                         trait TraitGenericMethod {
                             def <X> X bar(X x) { x }
                         }
                         class ConcreteClassOfTraitGenericMethod implements TraitGenericMethod {}
                         """);
  }

  public void testGenericTraitWithGenericMethodNoErrors() {
    doTestHighlighting("""
                         trait GenericTraitGenericMethod<X> {
                             public <T> T bar(T a) { a }
                         }
                         class ConcreteClassGenericTraitGenericMethod implements GenericTraitGenericMethod<String> {}
                         class GenericClassGenericTraitGenericMethod<Y> implements GenericTraitGenericMethod<Y> {}
                         """);
  }

  public void testGenericTraitNoErrors() {
    doTestHighlighting("""

                         trait GenericTrait<X> {
                             def X bar(X a) { a }
                         }
                         class GenericClassGenericTrait<Y> implements GenericTrait<Y> {}
                         class ConcreteClassGenericTrait implements GenericTrait<String> {}
                         """);
  }

  public void testAbstractMethodWithDefaultParametersInAbstractClass() {
    doTestHighlighting("""
                         abstract class A { abstract foo(x, y = 3) }
                         <error descr="Method 'foo' is not implemented">class B extends A</error> {}
                         """);
    doTestHighlighting("""
                         abstract class A { abstract foo(x, y = 3) }
                         class B extends A { def foo(x,y) {} }
                         """);
  }

  public void testAbstractMethodWithDefaultParametersInTrait() {
    doTestHighlighting("""
                         trait A { abstract foo(x, y = 3) }
                         <error descr="Method 'foo' is not implemented">class B implements A</error> {}
                         """);
    doTestHighlighting("""
                         trait A { abstract foo(x, y = 3) }
                         class B implements A { def foo(x,y) {} }
                         """);
  }

  public void testAbstractMethodWithDefaultParametersInTraitFromJava() {
    myFixture.addFileToProject("test.groovy", "trait T { abstract foo(x, y = 4) }");

    myFixture.configureByText(JavaFileType.INSTANCE, """
      <error descr="Class 'C' must either be declared abstract or implement abstract method 'foo(Object, Object)' in 'T'">class C implements T</error> {}
      """);
    myFixture.testHighlighting(false, false, false);

    myFixture.configureByText(JavaFileType.INSTANCE, """
      <error descr="Class 'C' must either be declared abstract or implement abstract method 'foo(Object, Object)' in 'T'">class C implements T</error> {
        public Object foo() { return null; }
        public Object foo(Object x) { return null; }
      }
      """);
    myFixture.testHighlighting(false, false, false);

    myFixture.configureByText(JavaFileType.INSTANCE, """
      class C implements T {
        public Object foo() { return null; }
        public Object foo(Object x) { return null; }
        public Object foo(Object x, Object y) { return null; }
      }
      """);
    myFixture.testHighlighting(false, false, false);
  }

  public void testTraitMethodUsages() {
    doTestHighlighting("""
                         trait T {
                             def getFoo() {}
                         }

                         class A implements T {}

                         new A().foo
                         """, GroovyUnusedDeclarationInspection.class);
  }

  public void testNoExceptionOnSuperReferenceInTraitWithoutSupertypes() {
    doTestHighlighting("""
                         trait SimpleTrait {
                           void foo() {
                             super.<warning descr="Cannot resolve symbol 'foo'">foo</warning>()
                           }
                         }
                         """);
  }

  public void testSpreadArgumentHighlight() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         class A {
                             def m(int a, String b) {

                             }
                             def m2(s) {
                                 m(<error>*[1,""]</error>)
                             }
                         }
                         """);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_3;
  }
}
