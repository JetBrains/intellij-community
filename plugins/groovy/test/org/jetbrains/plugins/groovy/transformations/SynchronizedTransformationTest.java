// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class SynchronizedTransformationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testLockIsUsedByAnnotatedInstanceMethod() {
    testHighlighting("""
                       final $lock = new Object()
                       @Synchronized
                       def foo() {}
                       """);
  }

  @Test
  public void testLockIsNotUsedByAnnotatedStaticMethod() {
    testHighlighting("""
                       final <warning descr="Property $lock is unused">$lock</warning> = new Object()
                       @Synchronized
                       static foo() {}
                       """);
  }

  @Test
  public void testStaticLockIsUsedByAnnotatedInstanceMethod() {
    testHighlighting("""
                       static final <error descr="Lock field '$lock' must not be static">$lock</error> = new Object()
                       @Synchronized
                       def foo() {}
                       """);
  }

  @Test
  public void testLockIsUsedByAnnotatedStaticMethod() {
    testHighlighting("""
                       static final $LOCK = new Object()
                       @Synchronized
                       static foo() {}
                       """);
  }

  @Test
  public void testLOCKIsNotUsedByAnnotatedInstanceMethod() {
    testHighlighting("""
                       static final <warning descr="Property $LOCK is unused">$LOCK</warning> = new Object()
                       @Synchronized
                       def foo() {}
                       """);
  }

  @Test
  public void testInstanceLOCKIsUsedByAnnotatedStaticMethod() {
    testHighlighting("""
                       final <error descr="Lock field '$LOCK' must be static">$LOCK</error> = new Object()
                       @Synchronized
                       static foo() {}
                       """);
  }

  @Test
  public void testLockNotFound() {
    testHighlighting("""
                       @Synchronized("<error descr="Lock field 'myLock' not found">myLock</error>")
                       def foo() {}
                       """);
  }

  @Test
  public void testCustomInstanceLockIsUsedByInstanceMethod() {
    testHighlighting("""
                       final myLock = new Object()
                       @Synchronized("myLock")
                       def foo() {}
                       """);
  }

  @Test
  public void testCustomInstanceLockIsUsedByStaticMethod() {
    testHighlighting("""
                       final <error descr="Lock field 'myLock' must be static">myLock</error> = new Object()
                       @Synchronized("myLock")
                       static foo() {}
                       """);
  }

  @Test
  public void testCustomStaticLockIsUsedByStaticMethod() {
    testHighlighting("""
                       static final myStaticLock = new Object()
                       @Synchronized("myStaticLock")
                       static foo() {}
                       """);
  }

  @Test
  public void testCustomStaticLockIsUsedByInstanceMethod() {
    testHighlighting("""
                       static final myStaticLock = new Object()
                       @Synchronized("myStaticLock")
                       def foo() {}
                       """);
  }

  @Test
  public void testNotAllowedOnAbstractMethods() {
    testHighlighting("""
                       import groovy.transform.Synchronized

                       class C {
                         @Synchronized
                         def foo() {}
                       }

                       interface I {
                         <error descr="@Synchronized not allowed on abstract method">@Synchronized</error>
                         def foo()
                       }

                       abstract class AC {
                         <error descr="@Synchronized not allowed on abstract method">@Synchronized</error>
                         abstract def foo()
                         def bar() {}
                       }

                       trait T {
                         <error descr="@Synchronized not allowed on abstract method">@Synchronized</error>
                         abstract def foo()
                         @Synchronized
                         def bar() {}
                       }
                       """, new InspectionProfileEntry[0]);
  }

  @Test
  public void testResolveToField() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
          class A {
            final myLock = new Object()
            @groovy.transform.Synchronized("myL<caret>ock")
            def foo() {}
          }
          """);
    ReadAction.runBlocking(() -> {
      PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertNotNull(ref);
      assertTrue(ref.resolve() instanceof GrField);
    });
  }

  @Test
  public void testCompleteFields() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
      class A {
      final foo = 1, bar = 2, baz = 3
      @groovy.transform.Synchronized("<caret>")
      def m() {}
      }
      """);
    myFixture.completeBasic();
    assertTrue(myFixture.getLookupElementStrings().containsAll(Arrays.asList("foo", "bar", "baz")));
  }

  @Test
  public void testRenameCustomLock() {
    testRename("""
                 final myLock = new Object()
                 @Synchronized("myL<caret>ock")
                 def foo() {}
                 """, "myLock2", """
                 final myLock2 = new Object()
                 @Synchronized("myLock2")
                 def foo() {}
                 """);
  }

  @Test
  public void testRenameLock() {
    testRename("""
                 final $l<caret>ock = new Object()
                 @Synchronized
                 def foo() {}
                 @Synchronized('$lock')
                 def foo2() {}
                 """, "myLock", """
                 final myLock = new Object()
                 @Synchronized('myLock')
                 def foo() {}
                 @Synchronized('myLock')
                 def foo2() {}
                 """);
  }

  private void testHighlighting(String text) {
    testHighlighting("""
                       import groovy.transform.Synchronized
                       class A {
                       """ + text + """
                       
                       }
                       new A().foo()
                       """,
                     new InspectionProfileEntry[]{new GroovyUnusedDeclarationInspection(), new UnusedDeclarationInspectionBase()});
  }

  private void testHighlighting(final String text, final InspectionProfileEntry[] inspections) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", text);
    myFixture.enableInspections(inspections);
    myFixture.checkHighlighting();
  }

  private void testRename(final String before, final String newName, final String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
                                            import groovy.transform.Synchronized
                                            class A {
                                            """ + before + """
                                            
                                            }
                                            """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.renameElementAtCaret(newName);
      myFixture.checkResult("""
                              import groovy.transform.Synchronized
                              class A {
                              """ + after + """
                              
                              }
                              """);
    });
  }
}