// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.GrUnusedIncDecInspection;
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection;

/**
 * @author Max Medvedev
 */
public class GrUnusedDefTest extends GrHighlightingTestBase {
  @Override
  public InspectionProfileEntry[] getCustomInspections() {
    return new InspectionProfileEntry[]{new UnusedDefInspection(), new GrUnusedIncDecInspection(), new GroovyUnusedDeclarationInspection(),
      new UnusedDeclarationInspectionBase(true)};
  }

  public void testUnusedVariable() { doTest(); }

  public void testDefinitionUsedInClosure() { doTest(); }

  public void testDefinitionUsedInClosure2() { doTest(); }

  public void testDefinitionUsedInSwitchCase() { doTest(); }

  public void testUnusedDefinitionForMethodMissing() { doTest(); }

  public void testPrefixIncrementCfa() { doTest(); }

  public void testIfIncrementElseReturn() { doTest(); }

  public void testSwitchControlFlow() { doTest(); }

  public void testUsageInInjection() { doTest(); }

  public void testUnusedDefsForArgs() { doTest(); }

  public void testUsedDefBeforeTry1() { doTest(); }

  public void testUsedDefBeforeTry2() { doTest(); }

  public void testUnusedInc() { doTest(); }

  public void testUsedInCatch() { doTest(); }

  public void testGloballyUnusedSymbols() { doTest(); }

  public void testGloballyUnusedInnerMethods() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    doTest();
  }

  public void testUnusedParameter() { doTest(); }

  public void testSuppressUnusedMethod() {
    doTestHighlighting("""
                         class <warning descr="Class Foo is unused">Foo</warning> {
                             @SuppressWarnings("GroovyUnusedDeclaration")
                             static def foo(int x) {
                                 print 2
                             }

                             static def <warning descr="Method bar is unused">bar</warning>() {}
                         }
                         """);
  }

  public void testUsedVar() {
    doTestHighlighting("""
                            def <warning descr="Method foo is unused">foo</warning>(xxx) {
                              if ((xxx = 5) || xxx) {
                                <warning descr="Assignment is not used">xxx</warning>=4
                              }
                            }

                            def <warning descr="Method foxo is unused">foxo</warning>(doo) {
                              def xxx = 'asdf'
                              if (!doo) {
                                println xxx
                                <warning descr="Assignment is not used">xxx</warning>=5
                              }
                            }
                         \s""");
  }

  public void testFallthroughInSwitch() {
    doTestHighlighting("""
                         def <warning descr="Method f is unused">f</warning>(String foo, int mode) {
                             switch (mode) {
                                 case 0: foo = foo.reverse()
                                 case 1: return foo
                             }
                         }

                         def <warning descr="Method f2 is unused">f2</warning>(String foo, int mode) {
                             switch (mode) {
                                 case 0: <warning descr="Assignment is not used">foo</warning> = foo.reverse()
                                 case 1: return 2
                             }
                         }
                         """);
  }

  public void testUnusedUnassignedVar() {
    doTestHighlighting("def <warning descr=\"Variable is not used\">abc</warning>");
  }

  public void testMethodReferencedViaIncapplicableCallIsUsed() {
    doTestHighlighting("""
                         static boolean fsdasdfsgsdsfadfgs(a, b) { a == b }
                         def bar() { fsdasdfsgsdsfadfgs("s") }
                         bar()
                         """);
  }

  public void testDelegate() {
    getFixture().addClass("""
                             package groovy.lang;
                             @Target({ElementType.FIELD, ElementType.METHOD})
                             public @interface Delegate {}
                             """);

    doTestHighlighting("""
                         class Foo {
                           @Delegate
                           Integer i
                           @Delegate
                           String bar() {}
                         }

                         new Foo()
                         """);
  }

  public void testUnusedSuppressesWarning() {
    doTestHighlighting("""
                         @SuppressWarnings("unused")
                         class Aaaa {}\s
                         """);
  }

  public void testSuppressWithUnused_() {
    doTestHighlighting("class <caret><warning descr=\"Class Aaaa is unused\">Aaaa</warning> {}");
    IntentionAction action = getFixture().findSingleIntention("Suppress for class");
    getFixture().launchAction(action);

    getFixture().checkResult("""
                               @SuppressWarnings('unused')
                               class <caret>Aaaa {}""");
  }

  public void testSafeDeletePreviewWithSingleClassInFile() {
    doTestHighlighting("class <caret><warning descr=\"Class Aaaa is unused\">Aaaa</warning> {}");
    IntentionAction action = getFixture().findSingleIntention("Safe delete 'Aaaa'");
    String actualPreview = getFixture().getIntentionPreviewText(action);

    TestCase.assertEquals("", actualPreview);
  }

  public void testUnusedDefForFieldsWithUnderscores() {
    doTestHighlighting("""
                         class B {
                             int <warning descr="Property _ is unused">_</warning>
                         }
                         
                         class C {
                             static int <warning descr="Property _ is unused">_</warning>
                         }
                         
                         new B()
                         new C()
                         """);
  }
}
