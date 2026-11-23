// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by Max Medvedev on 17/02/14
 */
@TestApplication
public class Groovy23HighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_3);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, GrHighlightingFixture.getHighlightingTestDataPath(), () -> new LocalInspectionTool[] {
      new GroovyAssignabilityCheckInspection(),
      new GrUnresolvedAccessInspection()
    });

  private void assertScript(String text) {
    highlighting.get().doTestHighlighting("import groovy.transform.CompileStatic\n" +
                                          "import groovy.transform.stc.ClosureParams\n" + text);
  }

  @Test
  public void testSam1() {
    highlighting.get().doTestHighlighting("""

                         interface Action<T, X> {
                             void execute(T t, X x)
                         }

                         public <T, X> void exec(T t, Action<T, X> f, X x) {
                         }

                         def foo() {
                             exec('foo', { String t, Integer x -> ; }, 1)
                             exec<warning descr="'exec' in '_' cannot be applied to '(java.lang.String, groovy.lang.Closure<java.lang.Void>, java.lang.Integer)'">('foo', { Integer t, Integer x -> ; }, 1)</warning>
                         }
                         """);
  }

  @Test
  public void testSam2() {
    highlighting.get().doTestHighlighting("""

                         interface Action<T> {
                             void execute(T t)
                         }

                         public <T> void exec(T t, Action<T> f) {
                         }

                         def foo() {
                             exec('foo') {print it.toUpperCase() ;print 2 }
                             exec('foo') {print <weak_warning descr="Cannot infer argument types">it.<warning descr="Cannot resolve symbol 'intValue'">intValue</warning>()</weak_warning> ;print 2 }
                         }
                         """);
  }

  @Test
  public void testSam3() {
    highlighting.get().doTestHighlighting("""
                          interface Action<T, X> {
                             void execute(T t, X x)
                         }

                         public <T, X> void exec(T t, Action<T, X> f, X x) {
                             f.execute(t, x)
                         }

                         def foo() {
                             exec('foo', { String s, Integer x -> print s + x }, 1)
                             exec<warning descr="'exec' in '_' cannot be applied to '(java.lang.String, groovy.lang.Closure, java.lang.Integer)'">('foo', { Integer s, Integer x -> print 9 }, 1)</warning>
                         }
                         """);
  }

  @Test
  public void testDefaultParameterInTraitMethods() {
    highlighting.get().doTestHighlighting("""
                         trait T {
                           abstract foo(x = 3);
                           def bar(y = 6) {}
                         }
                         """);
  }

  @Test
  public void testConcreteTraitProperty() {
    highlighting.get().doTestHighlighting("""
                         trait A {
                           def foo
                         }
                         class B implements A {}
                         """);
  }

  @Test
  public void testAbstractTraitProperty() {
    highlighting.get().doTestHighlighting("""
                         trait A {
                           abstract foo
                         }
                         <error descr="Method 'getFoo()' not implemented">class B implements A</error> {}
                         """);
    highlighting.get().doTestHighlighting("""
                         trait A {
                           abstract foo
                         }
                         class B implements A {
                           def foo
                         }
                         """);
  }

  @Test
  public void testTraitsHaveOnlyAbstractAndNonFinalMethods() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    GroovyFile file = (GroovyFile)myFixture.addFileToProject("T.groovy", """
      trait T {
        def foo
        abstract bar
        def baz() {}
        abstract doo()
      }
      """);
    ReadAction.runBlocking(() -> {
      GrTypeDefinition definition = (GrTypeDefinition)file.getClasses()[0];
      for (PsiMethod method : definition.getMethods()) {
        if (!method.getName().equals("baz")) {
          assertTrue(method.hasModifierProperty(PsiModifier.ABSTRACT));
        }

        assertFalse(method.hasModifierProperty(PsiModifier.FINAL));
      }
    });
  }

  @Test
  public void testTraitWithMethodWithDefaultParameters() {
    highlighting.get().doTestHighlighting("""
                         trait A {
                           def foo(a, b = null, c = null) {}
                         }
                         class B implements A {}
                         """);
  }

  @Test
  public void testTraitWithAbstractMethodWithDefaultParameters() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().doTestHighlighting("""
                         trait A {
                           abstract foo(a, b = null, c = null)
                         }
                         <error descr="Method 'foo()' not implemented">class B implements A</error> {}
                         """);
    ReadAction.runBlocking(() -> {
      GrTypeDefinition definition = (GrTypeDefinition)myFixture.findClass("B");
      Map<MethodSignature, CandidateInfo> map = OverrideImplementExploreUtil.getMapToOverrideImplement(definition, true);
      assertEquals(1, map.size());// need to implement only foo(a, b, c)
    });
  }

  @Test
  public void testTraitWithMiddleInterface() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().doTestHighlighting("""
                         trait T<E> {
                             E foo() { null }
                         }

                         interface I<G> extends T<G> {}

                         class C implements I<String> {}

                         new C().fo<caret>o()
                         """);
    ReadAction.runBlocking(() -> {
      PsiElement element = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      GrMethodCallExpression call = PsiTreeUtil.getParentOfType(element, GrMethodCallExpression.class);
      assertTrue(call.getType().equalsToText("java.lang.String"));
    });
  }

  @Test
  public void testTraitOrder() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.configureByText("_.groovy", """
      trait T1 {
          def foo() { 1 }
      }
      trait T2 {
          def foo() { 2 }
      }
      interface I1 extends T1 {}
      interface I2 extends T2 {}
      interface I3 extends I1, I2 {}
      class TT implements I3 {}
      new TT().fo<caret>o()
      """);
    ReadAction.runBlocking(() -> {
      PsiElement resolved = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()).resolve();
      assertTrue(resolved instanceof GrTraitMethod);
      PsiMethod original = ((GrTraitMethod)resolved).getPrototype();
      assertTrue(original instanceof GrMethod);
      assertEquals("T2", original.getContainingClass().getName());
    });
  }

  @Test
  public void testNonStaticInnerClassInTraitNotAllowed() {
    highlighting.get().doTestHighlighting("""
                         interface I {}

                         trait T {
                           def a = new <error descr="Non-static inner classes are not allowed in traits">I</error>() {}
                           def foo() {
                             new <error descr="Non-static inner classes are not allowed in traits">I</error>() {}
                           }
                           class <error descr="Non-static inner classes are not allowed in traits">A</error> {}
                           static class B {
                             def foo() {
                               new I() {} //no error here
                             }
                           }
                         }
                         """);
  }

  @Test
  public void testStaticTraitMembersNotResolvedInDirectAccess() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().doTestHighlighting("""
                         trait StaticsContainer {
                           public static boolean CALLED = false
                           static void init() { CALLED = true }
                           static foo() { init() }
                         }

                         class NoName implements StaticsContainer {}

                         NoName.init()
                         assert NoName.StaticsContainer__CALLED

                         StaticsContainer.<warning descr="Cannot resolve symbol 'init'">init</warning>()
                         StaticsContainer.<warning descr="Cannot resolve symbol 'CALLED'">CALLED</warning>
                         """);
    myFixture.configureByText("Consumer.java", """
      public class Consumer {
        public static void main(String[] args) {
          System.out.println(NoName.StaticsContainer__CALLED);
          System.out.println(StaticsContainer.<error descr="Cannot resolve symbol 'CALLED'">CALLED</error>);
        }
      }
      """);
    myFixture.testHighlighting();
  }

  @Test
  public void testClassInitializersInTraits() {
    highlighting.get().doTestHighlighting("""
                         trait T {
                           static {
                           }

                           {
                           }
                         }
                         """);
  }

  @Test
  public void testAbstractPropertyInClass() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.configureByText("_.groovy", """
          class A {
              abstract f
          }
          """);
    myFixture.checkHighlighting();
  }

  @Test
  public void testStaticModifierOnToplevelDefinitionNotTraitIsNotAllowed() {
    highlighting.get().doTestHighlighting("""
                         <error descr="Modifier 'static' not allowed here">static</error> class A {}
                         <error descr="Modifier 'static' not allowed here">static</error> interface I {}
                         static trait T {}
                         """);
  }

  @Test
  public void testDefaultMethodInInterfaces() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         interface I {
                             <error>default</error> int bar() {
                                 2
                             }
                         }

                         @CompileStatic
                         interface I2 {
                             <error>default</error> int bar() {
                                 2
                             }
                         }
                         """);
  }

  @Test
  public void testDefaultModifier() {
    highlighting.get().doTestHighlighting("""
                         <error>default</error> interface I {
                         }

                         trait T {
                             <error>default</error> int bar() {
                                 2
                             }
                         }

                         class C {
                             <error>default</error> int bar() {
                                 2
                             }
                         }
                         """);
  }

  @Test
  public void testFinalMethodInTrait() {
    highlighting.get().doTestHighlighting("""
                         trait ATrain {
                             final String getName() { "Name" }
                         }""");
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
    assertScript("""
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
    assertScript("""
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
                         <error descr="Method 'foo()' not implemented">class C implements T</error> {}
                         """);
  }

  @Test
  public void testNotImplementedInterfaceMethodWithTraitInHierarchy() {
    highlighting.get().doTestHighlighting("""
                         interface T {def foo()}
                         trait X implements T {}
                         <error descr="Method 'foo()' not implemented">class C implements X</error> {}
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
                         <error descr="Method 'foo()' not implemented">class B extends A</error> {}
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
                         <error descr="Method 'foo()' not implemented">class B implements A</error> {}
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
