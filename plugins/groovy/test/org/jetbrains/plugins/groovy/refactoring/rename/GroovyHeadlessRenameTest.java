// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.HeadlessRenameFailure;
import com.intellij.refactoring.rename.HeadlessRenameProcessor;
import com.intellij.refactoring.rename.HeadlessRenamePsiElementProcessor;
import com.intellij.refactoring.rename.HeadlessRenameResult;
import com.intellij.refactoring.rename.RenamePlan;
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.rename.RenameAliasImportedMethodProcessor.HeadlessRenameAliasImportedMethodProcessor;
import org.jetbrains.plugins.groovy.refactoring.rename.RenameGrFieldProcessor.HeadlessRenameGrFieldProcessor;
import org.jetbrains.plugins.groovy.transformations.impl.synch.SynchronizedRenameFieldProcessor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyHeadlessRenameTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testFieldAndItsAccessorUsagesAreRenamed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Counter.groovy", """
      class Counter {
        int hits
      }
      """);
    PsiFile reader = myFixture.addFileToProject("Reader.groovy", """
      class Reader {
        def read(Counter counter) {
          counter.hits + counter.getHits()
        }
      }
      """);
    PsiField hits = findField("Counter", "hits");
    assertProcessor(hits, HeadlessRenameGrFieldProcessor.class);

    rename(hits, "count");

    ReadAction.runBlocking(() -> {
      assertNotNull(findClass("Counter").findFieldByName("count", false), "the field kept the old name");
      assertFileContains(reader, "counter.count + counter.getCount()");
    });
  }

  @Test
  public void testFieldConflictWithAnExplicitGetterIsRefused() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Holder.groovy", """
      class Holder {
        def hits
        def getCount() { 1 }
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiField hits = findField("Holder", "hits");

      assertRefused(hits, "count");

      assertNotNull(findClass("Holder").findFieldByName("hits", false), "the rename wrote to the file");
    });
  }

  /** A rename of the implicit lock writes the new name into every annotation that used it. */
  @Test
  public void testImplicitLockOfSynchronizedIsWrittenIntoTheAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("Guarded.groovy", """
      import groovy.transform.Synchronized
      class Guarded {
        final $lock = new Object()
        @Synchronized
        def foo() {}
      }
      """);
    PsiField lock = findField("Guarded", "$lock");
    assertProcessor(lock, SynchronizedRenameFieldProcessor.class);

    rename(lock, "myLock");

    assertFileContains(file, "@Synchronized('myLock')");
  }

  @Test
  public void testOverridingMethodIsRenamedWithItsBase() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Base.groovy", """
      class Base {
        void run() {}
      }
      """);
    myFixture.addFileToProject("Impl.groovy", """
      class Impl extends Base {
        @Override
        void run() {}
      }
      """);
    PsiMethod implRun = findMethod("Impl", "run");
    assertProcessor(implRun, HeadlessRenameAliasImportedMethodProcessor.class);

    RenamePlan plan = plan(implRun, "execute");
    assertEquals(findMethod("Base", "run"), plan.getPrimaryElement(), "the target was not substituted with the base method");
    EdtTestUtil.runInEdtAndWait(() -> assertInstanceOf(HeadlessRenameResult.Applied.class, plan.apply()));

    ReadAction.runBlocking(() -> {
      assertThat(findClass("Base").findMethodsByName("execute", false)).hasSize(1);
      assertThat(findClass("Impl").findMethodsByName("execute", false)).hasSize(1);
    });
  }

  @Test
  public void testMethodWithADefaultParameterIsRenamed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile calc = myFixture.addFileToProject("Calc.groovy", """
      class Calc {
        def add(int a, int b = 1) { a + b }
        def use() { add(1) + add(1, 2) }
      }
      """);
    GrMethod add = ReadAction.computeBlocking(() -> PsiTreeUtil.findChildOfType(calc, GrMethod.class));
    assertNotNull(add, "no method add in the fixture");
    assertProcessor(add, RenameGrReflectedMethodProcessor.class);

    rename(add, "sum");

    assertFileContains(calc, "def use() { sum(1) + sum(1, 2) }");
  }

  @Test
  public void testClassAndItsUsageAreRenamed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Widget.groovy", """
      class Widget {}
      """);
    PsiFile holder = myFixture.addFileToProject("Holder.groovy", """
      class Holder {
        Widget widget = new Widget()
      }
      """);
    PsiClass widget = findClass("Widget");
    assertProcessor(widget, RenameAliasImportedClassProcessor.class);

    rename(widget, "Gadget");

    assertFileContains(holder, "Gadget widget = new Gadget()");
  }

  @Test
  public void testScriptRenameRenamesItsClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile script = myFixture.addFileToProject("runner.groovy", """
      println 'hi'
      """);
    PsiFile user = myFixture.addFileToProject("User.groovy", """
      class User {
        def start() { new runner().run() }
      }
      """);
    assertProcessor(script, RenameGroovyScriptProcessor.class);

    rename(script, "launcher.groovy");

    assertEquals("launcher.groovy", script.getName(), "the file kept the old name");
    assertFileContains(user, "new launcher().run()");
  }

  @Test
  public void testScriptNameThatIsNoIdentifierIsRefused() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile script = myFixture.addFileToProject("runner.groovy", """
      println 'hi'
      """);

    assertRefused(script, "my-runner.groovy");

    assertEquals("runner.groovy", script.getName(), "the rename wrote to the file");
  }

  @Test
  public void testImplicitAccessorIsRefused() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Bean.groovy", """
      class Bean {
        def hits
      }
      """);
    ReadAction.runBlocking(() -> {
      GrField hits = (GrField)findField("Bean", "hits");
      PsiMethod getter = hits.getGetters()[0];
      assertProcessor(getter, GrLightElementRenamer.class);

      HeadlessRenameResult result = HeadlessRenameProcessor.analyze(myFixture.getProject(), getter, "getCount");

      HeadlessRenameResult.Failed failed = assertInstanceOf(HeadlessRenameResult.Failed.class, result);
      assertEquals(HeadlessRenameFailure.TARGET_NOT_RENAMABLE, failed.getKind());
      assertNotNull(findClass("Bean").findFieldByName("hits", false), "the rename wrote to the file");
    });
  }

  /**
   * Only {@link PropertyRenameHandler} makes a {@link PropertyForRename}, so no headless target is one.
   * This asks the processor itself for the elements it collects.
   */
  @Test
  public void testPropertyCollectsItsFieldAndAccessors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Bean.groovy", """
      class Bean {
        def hits
        def getHits() { hits }
        void setHits(def value) { hits = value }
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiField hits = findField("Bean", "hits");
      PsiMethod getter = findMethod("Bean", "getHits");
      PsiMethod setter = findMethod("Bean", "setHits");
      PropertyForRename property = new PropertyForRename(List.of(hits, getter, setter), "hits", PsiManager.getInstance(myFixture.getProject()));
      HeadlessRenamePsiElementProcessor processor =
        (HeadlessRenamePsiElementProcessor)HeadlessRenamePsiElementProcessor.processorOf(property);
      assertInstanceOf(RenameGroovyPropertyProcessor.class, processor);

      Map<PsiElement, String> allRenames = new HashMap<>();
      allRenames.put(property, "count");
      processor.prepareRenamingHeadless(property, "count", allRenames);

      assertEquals(Map.of(hits, "count", getter, "getCount", setter, "setCount"), allRenames);
    });
  }

  private static RenamePlan plan(PsiElement element, String newName) {
    HeadlessRenameResult result = ReadAction.computeBlocking(() -> HeadlessRenameProcessor.analyze(element.getProject(), element, newName));
    return assertInstanceOf(HeadlessRenameResult.Planned.class, result).getPlan();
  }

  private static void rename(PsiElement element, String newName) {
    RenamePlan plan = plan(element, newName);
    EdtTestUtil.runInEdtAndWait(() -> assertInstanceOf(HeadlessRenameResult.Applied.class, plan.apply()));
  }

  private static void assertRefused(PsiElement element, String newName) {
    HeadlessRenameResult result = ReadAction.computeBlocking(() -> HeadlessRenameProcessor.analyze(element.getProject(), element, newName));
    HeadlessRenameResult.Refused refused = assertInstanceOf(HeadlessRenameResult.Refused.class, result);
    assertFalse(refused.getConflicts().isEmpty(), "a refusal must name its conflict");
  }

  private static void assertProcessor(PsiElement element, Class<? extends RenamePsiElementProcessorBase> processorClass) {
    assertInstanceOf(processorClass, ReadAction.computeBlocking(() -> HeadlessRenamePsiElementProcessor.processorOf(element)));
  }

  private static void assertFileContains(PsiFile file, String fragment) {
    String text = ReadAction.computeBlocking(() -> file.getText());
    assertTrue(text.contains(fragment), "no '" + fragment + "' in " + file.getName() + ": " + text);
  }

  private PsiField findField(String className, String name) {
    PsiClass psiClass = findClass(className);
    PsiField field = ReadAction.computeBlocking(() -> psiClass.findFieldByName(name, false));
    assertNotNull(field, "no field " + name + " in " + className);
    return field;
  }

  private PsiMethod findMethod(String className, String name) {
    PsiClass psiClass = findClass(className);
    PsiMethod[] methods = ReadAction.computeBlocking(() -> psiClass.findMethodsByName(name, false));
    assertThat(methods).hasSize(1);
    return methods[0];
  }

  private PsiClass findClass(String name) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass psiClass = ReadAction.computeBlocking(() -> myFixture.findClass(name));
    assertNotNull(psiClass, "no class " + name + " in the fixture");
    return psiClass;
  }
}
