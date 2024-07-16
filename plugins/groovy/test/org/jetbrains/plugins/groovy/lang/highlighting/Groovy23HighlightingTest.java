// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;

import java.util.Map;

/**
 * Created by Max Medvedev on 17/02/14
 */
public class Groovy23HighlightingTest extends GrHighlightingTestBase {
  public void testSam1() {
    doTestHighlighting("""

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

  public void testSam2() {
    doTestHighlighting("""

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

  public void testSam3() {
    doTestHighlighting("""
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

  public void testDefaultParameterInTraitMethods() {
    doTestHighlighting("""
                         trait T {
                           abstract foo(x = 3);
                           def bar(y = 6) {}
                         }
                         """);
  }

  public void testConcreteTraitProperty() {
    doTestHighlighting("""
                         trait A {
                           def foo
                         }
                         class B implements A {}
                         """);
  }

  public void testAbstractTraitProperty() {
    doTestHighlighting("""
                         trait A {
                           abstract foo
                         }
                         <error descr="Method 'getFoo' is not implemented">class B implements A</error> {}
                         """);
    doTestHighlighting("""
                         trait A {
                           abstract foo
                         }
                         class B implements A {
                           def foo
                         }
                         """);
  }

  public void testTraitsHaveOnlyAbstractAndNonFinalMethods() {
    GroovyFile file = (GroovyFile)myFixture.addFileToProject("T.groovy", """
      trait T {
        def foo
        abstract bar
        def baz() {}
        abstract doo()
      }
      """);
    GrTypeDefinition definition = (GrTypeDefinition)file.getClasses()[0];
    for (PsiMethod method : definition.getMethods()) {
      if (!method.getName().equals("baz")) {
        assertTrue(method.hasModifierProperty(PsiModifier.ABSTRACT));
      }

      assertFalse(method.hasModifierProperty(PsiModifier.FINAL));
    }
  }

  public void testTraitWithMethodWithDefaultParameters() {
    doTestHighlighting("""
                         trait A {
                           def foo(a, b = null, c = null) {}
                         }
                         class B implements A {}
                         """);
  }

  public void testTraitWithAbstractMethodWithDefaultParameters() {
    doTestHighlighting("""
                         trait A {
                           abstract foo(a, b = null, c = null)
                         }
                         <error descr="Method 'foo' is not implemented">class B implements A</error> {}
                         """);
    GrTypeDefinition definition = (GrTypeDefinition)myFixture.findClass("B");
    Map<MethodSignature, CandidateInfo> map = OverrideImplementExploreUtil.getMapToOverrideImplement(definition, true);
    assertEquals(1, map.size());// need to implement only foo(a, b, c)
  }

  public void testTraitWithMiddleInterface() {
    doTestHighlighting("""
                         trait T<E> {
                             E foo() { null }
                         }

                         interface I<G> extends T<G> {}

                         class C implements I<String> {}

                         new C().fo<caret>o()
                         """);
    PsiElement element = getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    GrMethodCallExpression call = PsiTreeUtil.getParentOfType(element, GrMethodCallExpression.class);
    assertTrue(call.getType().equalsToText("java.lang.String"));
  }

  public void testTraitOrder() {
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
    PsiElement resolved = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset()).resolve();
    assertTrue(resolved instanceof GrTraitMethod);
    PsiMethod original = ((GrTraitMethod)resolved).getPrototype();
    assertTrue(original instanceof GrMethod);
    assertEquals("T2", original.getContainingClass().getName());
  }

  public void testNonStaticInnerClassInTraitNotAllowed() {
    doTestHighlighting("""
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

  public void testStaticTraitMembersNotResolvedInDirectAccess() {
    doTestHighlighting("""
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

  public void testClassInitializersInTraits() {
    doTestHighlighting("""
                         trait T {
                           static {
                           }

                           {
                           }
                         }
                         """);
  }

  public void testAbstractPropertyInClass() {
    myFixture.configureByText("_.groovy", """
          class A {
              abstract f
          }
          """);
    myFixture.checkHighlighting();
  }

  public void testStaticModifierOnToplevelDefinitionNotTraitIsNotAllowed() {
    doTestHighlighting("""
                         <error descr="Modifier 'static' not allowed here">static</error> class A {}
                         <error descr="Modifier 'static' not allowed here">static</error> interface I {}
                         static trait T {}
                         """);
  }

  public void testDefaultMethodInInterfaces() {
    doTestHighlighting("""
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

  public void testDefaultModifier() {
    doTestHighlighting("""
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

  public void testFinalMethodInTrait() {
    doTestHighlighting("""
                         trait ATrain {
                             final String getName() { "Name" }
                         }""");
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_3;
  }

  @Override
  public final InspectionProfileEntry[] getCustomInspections() {
    return new InspectionProfileEntry[] {
      new GroovyAssignabilityCheckInspection(),
      new GrUnresolvedAccessInspection()
    };
  }
}
