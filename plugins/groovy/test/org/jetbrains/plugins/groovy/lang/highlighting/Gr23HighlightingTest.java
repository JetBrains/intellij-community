// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

/**
 * Created by Max Medvedev on 27/02/14
 */
@TestApplication
public class Gr23HighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_3);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, GrHighlightingFixture.getHighlightingTestDataPath(), () -> new LocalInspectionTool[]{new GroovyAssignabilityCheckInspection(), new GrUnresolvedAccessInspection()});

  public void assertScript(String text) {
    highlighting.get().doTestHighlighting("import groovy.transform.CompileStatic\nimport groovy.transform.stc.ClosureParams\n" + text);
  }

  public void shouldFailWithMessages(String text) {
    assertScript(text);
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testTrait1() {
    highlighting.get().doTestHighlighting("""
                         trait X {
                           void foo() {}
                         }
                         """);
  }

  @Test
  public void testTrait2() {
    highlighting.get().doTestHighlighting("""
                         interface A {}
                         trait X implements A {
                           void foo() {}
                         }
                         """);
  }

  @Test
  public void testTrait3() {
    highlighting.get().doTestHighlighting("""
                         class A {}
                         trait X extends <error descr="Only traits are expected here">A</error> {
                           void foo() {}
                         }
                         """);
  }

  @Test
  public void testTrait4() {
    highlighting.get().doTestHighlighting("""
                         trait A {}
                         trait X extends A {
                           void foo() {}
                         }
                         """);
  }

  @Test
  public void testTraitExtendsList() {
    highlighting.get().doTestHighlighting("trait B extends <error descr=\"Only traits are expected here\">HashMap</error> {}");
  }

  @Test
  public void testIncIsNotAllowedInTraits() {
    highlighting.get().doTestHighlighting("""
                         trait C {
                           def x = 5
                           def foo() {
                             <error descr="++ expressions on trait fields/properties are not supported in traits">x++</error>
                           }
                         }
                         """);
  }

  @Test
  public void testTraitAsAnonymous() {
    highlighting.get().doTestHighlighting("""
                         trait T {}

                         new <error descr="Anonymous classes from traits are available since Groovy 2.5.2">T</error>(){}
                         """);
  }

  @Test
  public void testAbstractMethodsInTrait() {
    highlighting.get().doTestHighlighting("""
                         trait T {
                           def foo() {}
                           def <error descr="Not abstract method should have body">bar</error>()
                           abstract baz()
                           abstract xyz() <error descr="Abstract methods must not have body">{}</error>
                         }
                         """);
  }

  @Test
  public void testTraitImplementsInterfaceMethod() {
    highlighting.get().doTestHighlighting("""
                         interface A {def foo()}
                         trait B implements A { def foo() {}}
                         class C implements B {}
                         """);
  }

  @Test
  public void testNotImplementedTraitMethod() {
    highlighting.get().doTestHighlighting("""
                         trait T {abstract def foo()}
                         <error descr="Method 'foo' is not implemented">class C implements T</error> {}
                         """);
  }

  @Test
  public void testNotImplementedInterfaceMethodWithTraitInHierarchy() {
    highlighting.get().doTestHighlighting("""
                         interface T {def foo()}
                         trait X implements T {}
                         <error descr="Method 'foo' is not implemented">class C implements X</error> {}
                         """);
  }

  @Test
  public void testTraitMethodsCannotBeProtected() {
    highlighting.get().doTestHighlighting("trait T {<error descr=\"Trait methods are not allowed to be protected\">protected</error> foo(){}}");
  }

  @Test
  public void testTraitCanImplementNumerousTraits() {
    highlighting.get().doTestHighlighting("""
                         trait A{}
                         trait B{}

                         trait D extends A {}
                         trait E implements A {}
                         trait F extends A implements B {}
                         trait G implements A, B {}
                         """);
  }

  @Test
  public void testTraitWithGenericMethodNoErrors() {
    highlighting.get().doTestHighlighting("""
                         trait TraitGenericMethod {
                             def <X> X bar(X x) { x }
                         }
                         class ConcreteClassOfTraitGenericMethod implements TraitGenericMethod {}
                         """);
  }

  @Test
  public void testGenericTraitWithGenericMethodNoErrors() {
    highlighting.get().doTestHighlighting("""
                         trait GenericTraitGenericMethod<X> {
                             public <T> T bar(T a) { a }
                         }
                         class ConcreteClassGenericTraitGenericMethod implements GenericTraitGenericMethod<String> {}
                         class GenericClassGenericTraitGenericMethod<Y> implements GenericTraitGenericMethod<Y> {}
                         """);
  }

  @Test
  public void testGenericTraitNoErrors() {
    highlighting.get().doTestHighlighting("""

                         trait GenericTrait<X> {
                             def X bar(X a) { a }
                         }
                         class GenericClassGenericTrait<Y> implements GenericTrait<Y> {}
                         class ConcreteClassGenericTrait implements GenericTrait<String> {}
                         """);
  }

  @Test
  public void testAbstractMethodWithDefaultParametersInAbstractClass() {
    highlighting.get().doTestHighlighting("""
                         abstract class A { abstract foo(x, y = 3) }
                         <error descr="Method 'foo' is not implemented">class B extends A</error> {}
                         """);
    highlighting.get().doTestHighlighting("""
                         abstract class A { abstract foo(x, y = 3) }
                         class B extends A { def foo(x,y) {} }
                         """);
  }

  @Test
  public void testAbstractMethodWithDefaultParametersInTrait() {
    highlighting.get().doTestHighlighting("""
                         trait A { abstract foo(x, y = 3) }
                         <error descr="Method 'foo' is not implemented">class B implements A</error> {}
                         """);
    highlighting.get().doTestHighlighting("""
                         trait A { abstract foo(x, y = 3) }
                         class B implements A { def foo(x,y) {} }
                         """);
  }

  @Test
  public void testAbstractMethodWithDefaultParametersInTraitFromJava() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
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

  @Test
  public void testTraitMethodUsages() {
    highlighting.get().doTestHighlighting("""
                         trait T {
                             def getFoo() {}
                         }

                         class A implements T {}

                         new A().foo
                         """, GroovyUnusedDeclarationInspection.class);
  }

  @Test
  public void testNoExceptionOnSuperReferenceInTraitWithoutSupertypes() {
    highlighting.get().doTestHighlighting("""
                         trait SimpleTrait {
                           void foo() {
                             super.<warning descr="Cannot resolve symbol 'foo'">foo</warning>()
                           }
                         }
                         """);
  }

  @Test
  public void testSpreadArgumentHighlight() {
    highlighting.get().doTestHighlighting("""
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
}
