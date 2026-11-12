// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.GrUnusedIncDecInspection;
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrUnusedDefTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, GrHighlightingFixture.getHighlightingTestDataPath(), () -> new InspectionProfileEntry[]{new UnusedDefInspection(), new GrUnusedIncDecInspection(), new GroovyUnusedDeclarationInspection(),
      new UnusedDeclarationInspectionBase(true)});

  @Test
  public void testUnusedVariable() { highlighting.get().doTest(); }

  @Test
  public void testDefinitionUsedInClosure() { highlighting.get().doTest(); }

  @Test
  public void testDefinitionUsedInClosure2() { highlighting.get().doTest(); }

  @Test
  public void testDefinitionUsedInSwitchCase() { highlighting.get().doTest(); }

  @Test
  public void testUnusedDefinitionForMethodMissing() { highlighting.get().doTest(); }

  @Test
  public void testPrefixIncrementCfa() { highlighting.get().doTest(); }

  @Test
  public void testIfIncrementElseReturn() { highlighting.get().doTest(); }

  @Test
  public void testSwitchControlFlow() { highlighting.get().doTest(); }

  @Test
  public void testUsageInInjection() { highlighting.get().doTest(); }

  @Test
  public void testUnusedDefsForArgs() { highlighting.get().doTest(); }

  @Test
  public void testUsedDefBeforeTry1() { highlighting.get().doTest(); }

  @Test
  public void testUsedDefBeforeTry2() { highlighting.get().doTest(); }

  @Test
  public void testUnusedInc() { highlighting.get().doTest(); }

  @Test
  public void testUsedInCatch() { highlighting.get().doTest(); }

  @Test
  public void testGloballyUnusedSymbols() { highlighting.get().doTest(); }

  @Test
  public void testUnusedTypeDefinitions() { highlighting.get().doTest(); }

  @Test
  public void testGloballyUnusedInnerMethods() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.addClass("package junit.framework; public class TestCase {}");
    highlighting.get().doTest();
  }

  @Test
  public void testUnusedParameter() { highlighting.get().doTest(); }

  @Test
  public void testSuppressUnusedMethod() {
    highlighting.get().doTestHighlighting("""
                         class <warning descr="Class Foo is unused">Foo</warning> {
                             @SuppressWarnings("GroovyUnusedDeclaration")
                             static def foo(int x) {
                                 print 2
                             }

                             static def <warning descr="Method bar is unused">bar</warning>() {}
                         }
                         """);
  }

  @Test
  public void testUsedVar() {
    highlighting.get().doTestHighlighting("""
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

  @Test
  public void testFallthroughInSwitch() {
    highlighting.get().doTestHighlighting("""
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

  @Test
  public void testUnusedUnassignedVar() {
    highlighting.get().doTestHighlighting("def <warning descr=\"Variable is not used\">abc</warning>");
  }

  @Test
  public void testMethodReferencedViaIncapplicableCallIsUsed() {
    highlighting.get().doTestHighlighting("""
                         static boolean fsdasdfsgsdsfadfgs(a, b) { a == b }
                         def bar() { fsdasdfsgsdsfadfgs("s") }
                         bar()
                         """);
  }

  @Test
  public void testDelegate() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.addClass("""
                             package groovy.lang;
                             @Target({ElementType.FIELD, ElementType.METHOD})
                             public @interface Delegate {}
                             """);

    highlighting.get().doTestHighlighting("""
                         class Foo {
                           @Delegate
                           Integer i
                           @Delegate
                           String bar() {}
                         }

                         new Foo()
                         """);
  }

  @Test
  public void testUnusedSuppressesWarning() {
    highlighting.get().doTestHighlighting("""
                         @SuppressWarnings("unused")
                         class Aaaa {}\s
                         """);
  }

  @Test
  public void testSuppressWithUnused_() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().doTestHighlighting("class <caret><warning descr=\"Class Aaaa is unused\">Aaaa</warning> {}");
    IntentionAction action = myFixture.findSingleIntention("Suppress for class");
    myFixture.launchAction(action);

    myFixture.checkResult("""
                               @SuppressWarnings('unused')
                               class <caret>Aaaa {}""");
  }

  @Test
  public void testSafeDeletePreviewWithSingleClassInFile() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().doTestHighlighting("class <caret><warning descr=\"Class Aaaa is unused\">Aaaa</warning> {}");
    IntentionAction action = myFixture.findSingleIntention("Safe delete 'Aaaa'");
    String actualPreview = myFixture.getIntentionPreviewText(action);

    assertEquals("", actualPreview);
  }

  @Test
  public void testUnusedDefForFieldsWithUnderscores() {
    highlighting.get().doTestHighlighting("""
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
