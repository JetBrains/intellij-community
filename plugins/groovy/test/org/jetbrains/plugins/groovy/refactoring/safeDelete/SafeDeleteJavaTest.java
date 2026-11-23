// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.safeDelete;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.SafeDeleteTestSupport;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.bundled.BundledGroovy;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The tests of the safe delete with Groovy code, run with the legacy flow. The subclasses run the same cases on the ModPlan engine.
 *
 * @author Max Medvedev
 */
@TestApplication
public class SafeDeleteJavaTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  protected final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "refactoring/safeDeleteJavaParameter/");

  @TestDisposable
  protected Disposable testDisposable;

  /** The search resolves classes through the bundled Groovy jar, which a local Bazel run on Windows keeps outside the allowed roots. */
  @BeforeEach
  public void allowBundledGroovyAccess() {
    VfsRootAccess.allowRootAccess(testDisposable, BundledGroovy.getBundledGroovyFile().get().getPath());
  }

  @Test
  public void testGroovyCall() {
    doTest("""
             class A {
               void foo(int ba<caret>r) {}
             }
             """, """
             new A().foo(2)
             """, """
             new A().foo()
             """);
  }

  @Test
  public void testGroovyDocRef() {
    doTest("""
             class A {
               void foo(int ba<caret>r, long baz) {}
             }
             """, """
             /**
             @see A#foo(int,long)
             */
             class X{}
             """, """
             /**
             @see A#foo(long)
             */
             class X{}
             """);
  }

  @Test
  public void testGroovyTypeArgs() {
    doTest("""
             class A<<caret>T> {
             }
             """, """
             class X {
               A<String> a = new A<String>()
             }
             """, """
             class X {
               A a = new A()
             }
             """);
  }

  @Test
  public void testGroovyTypeArgs2() {
    doTest("""
             class A<<caret>T, K> {
             }
             """, """
             class X {
               A<String, String> a = new A<String, String>()
             }
             """, """
             class X {
               A<String> a = new A<String>()
             }
             """);
  }

  @Test
  public void testUnusedGroovyProperty() {
    doTest(List.of(new TestFile("A.groovy", """
             class A {
                 def fo<caret>o
                 void bar() {}
             }
             """)),
           List.of(new TestFile("A.groovy", """
             class A {
                 void bar() {}
             }
             """)));
  }

  @Test
  public void testGroovyPropertyUsedThroughAccessors() {
    doConflictTest(List.of(new TestFile("A.groovy", """
                     class A {
                         def fo<caret>o
                     }
                     """),
                   new TestFile("B.groovy", """
                     new A().foo = 1
                     println new A().foo
                     """),
                   new TestFile("C.java", """
                     class C {
                       Object use() { return new A().getFoo(); }
                     }
                     """)),
                   """
                     Usage of field <b><code>A.foo</code></b> that is not safe to delete.
                     Usage of field <b><code>A.foo</code></b> that is not safe to delete.
                     Usage of field <b><code>A.foo</code></b> that is not safe to delete.""");
  }

  @Test
  public void testGroovyFieldWithExplicitAccessors() {
    doTest(List.of(new TestFile("A.groovy", """
             class A {
                 private int fo<caret>o
                 int getFoo() { foo }
                 void setFoo(int foo) { this.foo = foo }
                 void bar() {}
             }
             """)),
           List.of(new TestFile("A.groovy", """
             class A {
                 void bar() {}
             }
             """)));
  }

  @Test
  public void testUnusedGroovyMethodWithDefaultParameter() {
    doTest(List.of(new TestFile("A.groovy", """
             class A {
                 void fo<caret>o(int x = 1) {}
                 void bar() {}
             }
             """)),
           List.of(new TestFile("A.groovy", """
             class A {
                 void bar() {}
             }
             """)));
  }

  @Test
  public void testGroovyMethodWithDefaultParameterUsedWithoutArgument() {
    doConflictTest(List.of(new TestFile("A.groovy", """
                     class A {
                         void fo<caret>o(int x = 1) {}
                     }
                     """),
                   new TestFile("B.groovy", """
                     new A().foo()
                     """)),
                   "Usage of method <b><code>A.foo(int)</code></b> that is not safe to delete.");
  }

  @Test
  public void testGroovyClassUsedOnlyInImport() {
    doTest(List.of(new TestFile("B.groovy", """
             import p.<caret>A
             class B {}
             """),
                   new TestFile("p/A.groovy", """
                     package p
                     class A {}
                     """)),
           List.of(new TestFile("B.groovy", """
                     class B {}
                     """),
                   new TestFile("p/A.groovy", null)));
  }

  @Test
  public void testOnlyGroovyClassOfFile() {
    doTest(List.of(new TestFile("A.groovy", """
             class <caret>A {}
             """)),
           List.of(new TestFile("A.groovy", null)));
  }

  /** A GroovyDoc reference is not a safe usage, unlike a Javadoc one. */
  @Test
  public void testGroovyMethodInGroovyDoc() {
    doConflictTest(List.of(new TestFile("A.groovy", """
                     class A {
                         void fo<caret>o() {}
                     }
                     """),
                   new TestFile("B.groovy", """
                     /**
                      * @see A#foo()
                      */
                     class B {}
                     """)),
                   "Usage of method <b><code>A.foo()</code></b> that is not safe to delete.");
  }

  @Test
  public void testJavaParameterWithGroovyOverriderAndCall() {
    doTest(List.of(new TestFile("Base.java", """
             class Base {
               void foo(int ba<caret>r) {}
             }
             """),
                   new TestFile("Sub.groovy", """
                     class Sub extends Base {
                         void foo(int bar) {}
                     }
                     """),
                   new TestFile("Use.groovy", """
                     new Sub().foo(1)
                     """)),
           List.of(new TestFile("Sub.groovy", """
                     class Sub extends Base {
                         void foo() {}
                     }
                     """),
                   new TestFile("Use.groovy", """
                     new Sub().foo()
                     """)));
  }

  @Test
  public void testJavaMethodWithGroovyOverrider() {
    doTest(List.of(new TestFile("Base.java", """
             class Base {
               void fo<caret>o() {}
             }
             """),
                   new TestFile("Sub.groovy", """
                     class Sub extends Base {
                         @Override
                         void foo() {}
                     }
                     """)),
           List.of(new TestFile("Base.java", """
                     class Base {
                     }
                     """),
                   new TestFile("Sub.groovy", """
                     class Sub extends Base {
                     }
                     """)));
  }

  @Test
  public void testGroovyMethodThatImplementsJavaMethod() {
    doConflictTest(List.of(new TestFile("A.groovy", """
                     class A implements I {
                         void fo<caret>o() {}
                     }
                     """),
                   new TestFile("I.java", """
                     interface I {
                       void foo();
                     }
                     """)),
                   "Method <b><code>A.foo()</code></b> implements method <b><code>I.foo()</code></b>.");
  }

  @Test
  public void testTwoGroovyMethods() {
    doTest(List.of(new TestFile("A.groovy", """
             class A {
                 void fo<caret>o() {}
                 void ba<caret>r() { foo() }
                 void baz() {}
             }
             """)),
           List.of(new TestFile("A.groovy", """
             class A {
                 void baz() {}
             }
             """)));
  }

  @Test
  public void testMethodOfGroovyScript() {
    doTest(List.of(new TestFile("s.groovy", """
             def fo<caret>o() {}
             println 1
             """)),
           List.of(new TestFile("s.groovy", """
             println 1
             """)));
  }

  @Test
  public void testGroovyMethodMentionedInCommentAndString() {
    doTest(List.of(new TestFile("A.groovy", """
             class A {
                 void fo<caret>o() {}
             }
             """),
                   new TestFile("B.groovy", """
                     // calls foo
                     println 'foo'
                     """)),
           Map.of(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true, ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, true),
           List.of(new TestFile("A.groovy", """
                     class A {
                     }
                     """),
                   new TestFile("B.groovy", """
                     // calls foo
                     println 'foo'
                     """)),
           List.of("B.groovy:1: // calls foo", "B.groovy:2: println 'foo'"));
  }

  /**
   * A file of a case: a path in the source root and its text. A text of the files before the deletion has {@code <caret>} at each element
   * to delete, only in the first file. A {@code null} text of the files after the deletion expects that the deletion removed the file.
   */
  protected record TestFile(@NotNull String path, @Nullable String text) {
  }

  /** @return the answers that the legacy unit-test mode gives and the ModPlan flows do not give by themselves */
  protected @NotNull Map<ModPlanOption<?>, Object> defaultAnswers() {
    return Map.of();
  }

  /** Deletes {@code elements} as the safe delete action does, after {@link SafeDeleteTestSupport#applyOptions} gave the answers. */
  protected void performSafeDelete(@NotNull List<PsiElement> elements,
                                   @NotNull Map<ModPlanOption<?>, Object> answers,
                                   @NotNull SafeDeleteTestSupport.NonCodeUsageRecorder nonCodeUsageRecorder) {
    SafeDeleteHandler.invoke(codeInsightFixture.get().getProject(), elements.toArray(PsiElement.EMPTY_ARRAY), true);
  }

  private void doTest(String java, String groovy, String groovyAfter) {
    doTest(List.of(new TestFile("test.java", java), new TestFile("test.groovy", groovy)), List.of(new TestFile("test.groovy", groovyAfter)));
  }

  protected void doTest(@NotNull List<TestFile> filesBefore, @NotNull List<TestFile> filesAfter) {
    doTest(filesBefore, Map.of(), filesAfter, List.of());
  }

  /**
   * Deletes the elements at the carets with {@code answers} on top of {@link #defaultAnswers()}, and checks the text of
   * {@code filesAfter} and the lines of the non-code usages that the flow found.
   */
  protected void doTest(@NotNull List<TestFile> filesBefore,
                        @NotNull Map<ModPlanOption<?>, Object> answers,
                        @NotNull List<TestFile> filesAfter,
                        @NotNull List<String> nonCodeUsageLines) {
    SafeDeleteTestSupport.NonCodeUsageRecorder nonCodeUsageRecorder = safeDelete(filesBefore, answers);
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      for (TestFile fileAfter : filesAfter) {
        VirtualFile virtualFile = myFixture.getTempDirFixture().getFile(fileAfter.path());
        if (fileAfter.text() == null) {
          assertNull(virtualFile, fileAfter.path());
        }
        else {
          assertNotNull(virtualFile, fileAfter.path());
          PsiFile psiFile = PsiManager.getInstance(myFixture.getProject()).findFile(virtualFile);
          assertNotNull(psiFile, fileAfter.path());
          assertEquals(fileAfter.text(), psiFile.getText(), fileAfter.path());
        }
      }
    });
    assertEquals(nonCodeUsageLines, nonCodeUsageRecorder.lines());
  }

  /** Checks that the deletion of the elements at the carets stops at conflicts with {@code conflictMessages}, sorted and joined. */
  protected void doConflictTest(@NotNull List<TestFile> filesBefore, @NotNull String conflictMessages) {
    BaseRefactoringProcessor.ConflictsInTestsException conflicts =
      assertThrows(BaseRefactoringProcessor.ConflictsInTestsException.class, () -> safeDelete(filesBefore, Map.of()));
    assertEquals(conflictMessages, conflicts.getMessage());
  }

  private @NotNull SafeDeleteTestSupport.NonCodeUsageRecorder safeDelete(@NotNull List<TestFile> filesBefore,
                                                                         @NotNull Map<ModPlanOption<?>, Object> answers) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestFile caretFile = filesBefore.getFirst();
    myFixture.configureByText(caretFile.path(), caretFile.text());
    for (TestFile file : filesBefore.subList(1, filesBefore.size())) {
      myFixture.addFileToProject(file.path(), file.text());
    }
    Map<ModPlanOption<?>, Object> allAnswers = new HashMap<>(defaultAnswers());
    allAnswers.putAll(answers);
    SafeDeleteTestSupport.NonCodeUsageRecorder nonCodeUsageRecorder = new SafeDeleteTestSupport.NonCodeUsageRecorder();
    SafeDeleteTestSupport.applyOptions(allAnswers, nonCodeUsageRecorder, testDisposable);

    EdtTestUtil.runInEdtAndWait(() -> performSafeDelete(elementsAtCarets(myFixture.getEditor()), allAnswers, nonCodeUsageRecorder));
    return nonCodeUsageRecorder;
  }

  private static @NotNull List<PsiElement> elementsAtCarets(@NotNull Editor editor) {
    List<PsiElement> elements = new ArrayList<>();
    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      PsiElement element = TargetElementUtil.getInstance().findTargetElement(
        editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED, caret.getOffset());
      assertNotNull(element, "no element at offset " + caret.getOffset());
      elements.add(element);
    }
    return elements;
  }
}
