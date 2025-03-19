// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.junit.Assert;

import java.util.Arrays;

public class SynchronizedTransformationTest extends LightGroovyTestCase {
  @Override
  @NotNull
  public LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public void testLockIsUsedByAnnotatedInstanceMethod() {
    testHighlighting("""
                       final $lock = new Object()
                       @Synchronized
                       def foo() {}
                       """);
  }

  public void testLockIsNotUsedByAnnotatedStaticMethod() {
    testHighlighting("""
                       final <warning descr="Property $lock is unused">$lock</warning> = new Object()
                       @Synchronized
                       static foo() {}
                       """);
  }

  public void testStaticLockIsUsedByAnnotatedInstanceMethod() {
    testHighlighting("""
                       static final <error descr="Lock field '$lock' must not be static">$lock</error> = new Object()
                       @Synchronized
                       def foo() {}
                       """);
  }

  public void testLockIsUsedByAnnotatedStaticMethod() {
    testHighlighting("""
                       static final $LOCK = new Object()
                       @Synchronized
                       static foo() {}
                       """);
  }

  public void testLOCKIsNotUsedByAnnotatedInstanceMethod() {
    testHighlighting("""
                       static final <warning descr="Property $LOCK is unused">$LOCK</warning> = new Object()
                       @Synchronized
                       def foo() {}
                       """);
  }

  public void testInstanceLOCKIsUsedByAnnotatedStaticMethod() {
    testHighlighting("""
                       final <error descr="Lock field '$LOCK' must be static">$LOCK</error> = new Object()
                       @Synchronized
                       static foo() {}
                       """);
  }

  public void testLockNotFound() {
    testHighlighting("""
                       @Synchronized("<error descr="Lock field 'myLock' not found">myLock</error>")
                       def foo() {}
                       """);
  }

  public void testCustomInstanceLockIsUsedByInstanceMethod() {
    testHighlighting("""
                       final myLock = new Object()
                       @Synchronized("myLock")
                       def foo() {}
                       """);
  }

  public void testCustomInstanceLockIsUsedByStaticMethod() {
    testHighlighting("""
                       final <error descr="Lock field 'myLock' must be static">myLock</error> = new Object()
                       @Synchronized("myLock")
                       static foo() {}
                       """);
  }

  public void testCustomStaticLockIsUsedByStaticMethod() {
    testHighlighting("""
                       static final myStaticLock = new Object()
                       @Synchronized("myStaticLock")
                       static foo() {}
                       """);
  }

  public void testCustomStaticLockIsUsedByInstanceMethod() {
    testHighlighting("""
                       static final myStaticLock = new Object()
                       @Synchronized("myStaticLock")
                       def foo() {}
                       """);
  }

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

  public void testResolveToField() {
    myFixture.configureByText("a.groovy", """
          class A {
            final myLock = new Object()
            @groovy.transform.Synchronized("myL<caret>ock")
            def foo() {}
          }
          """);
    PsiReference ref = getFile().findReferenceAt(getEditor().getCaretModel().getOffset());
    Assert.assertNotNull(ref);
    Assert.assertTrue(ref.resolve() instanceof GrField);
  }

  public void testCompleteFields() {
    myFixture.configureByText("a.groovy", """
      class A {
      final foo = 1, bar = 2, baz = 3
      @groovy.transform.Synchronized("<caret>")
      def m() {}
      }
      """);
    myFixture.completeBasic();
    Assert.assertTrue(myFixture.getLookupElementStrings().containsAll(Arrays.asList("foo", "bar", "baz")));
  }

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
    myFixture.configureByText("_.groovy", text);
    myFixture.enableInspections(inspections);
    myFixture.checkHighlighting();
  }

  private void testRename(final String before, final String newName, final String after) {
    myFixture.configureByText("_.groovy", """
                                            import groovy.transform.Synchronized
                                            class A {
                                            """ + before + """
                                            
                                            }
                                            """);
    myFixture.renameElementAtCaret(newName);
    myFixture.checkResult("""
                            import groovy.transform.Synchronized
                            class A {
                            """ + after + """
                            
                            }
                            """);
  }
}