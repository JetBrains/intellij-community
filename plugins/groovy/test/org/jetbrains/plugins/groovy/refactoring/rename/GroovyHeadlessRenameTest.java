// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.HeadlessRenameFailure;
import com.intellij.refactoring.rename.HeadlessRenameProcessor;
import com.intellij.refactoring.rename.HeadlessRenamePsiElementProcessor;
import com.intellij.refactoring.rename.HeadlessRenameResult;
import com.intellij.refactoring.rename.RenamePlan;
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.rename.RenameAliasImportedMethodProcessor.HeadlessRenameAliasImportedMethodProcessor;
import org.jetbrains.plugins.groovy.refactoring.rename.RenameGrFieldProcessor.HeadlessRenameGrFieldProcessor;
import org.jetbrains.plugins.groovy.transformations.impl.synch.SynchronizedRenameFieldProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroovyHeadlessRenameTest extends LightGroovyTestCase {
  @Override
  public @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public void testFieldAndItsAccessorUsagesAreRenamed() {
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

    assertNotNull("the field kept the old name", findClass("Counter").findFieldByName("count", false));
    assertFileContains(reader, "counter.count + counter.getCount()");
  }

  public void testFieldConflictWithAnExplicitGetterIsRefused() {
    myFixture.addFileToProject("Holder.groovy", """
      class Holder {
        def hits
        def getCount() { 1 }
      }
      """);
    PsiField hits = findField("Holder", "hits");

    assertRefused(hits, "count");

    assertNotNull("the rename wrote to the file", findClass("Holder").findFieldByName("hits", false));
  }

  /** A rename of the implicit lock writes the new name into every annotation that used it. */
  public void testImplicitLockOfSynchronizedIsWrittenIntoTheAnnotation() {
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

  public void testOverridingMethodIsRenamedWithItsBase() {
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
    assertEquals("the target was not substituted with the base method", findMethod("Base", "run"), plan.getPrimaryElement());
    assertInstanceOf(plan.apply(), HeadlessRenameResult.Applied.class);

    assertSize(1, findClass("Base").findMethodsByName("execute", false));
    assertSize(1, findClass("Impl").findMethodsByName("execute", false));
  }

  public void testMethodWithADefaultParameterIsRenamed() {
    PsiFile calc = myFixture.addFileToProject("Calc.groovy", """
      class Calc {
        def add(int a, int b = 1) { a + b }
        def use() { add(1) + add(1, 2) }
      }
      """);
    GrMethod add = PsiTreeUtil.findChildOfType(calc, GrMethod.class);
    assertNotNull("no method add in the fixture", add);
    assertProcessor(add, RenameGrReflectedMethodProcessor.class);

    rename(add, "sum");

    assertFileContains(calc, "def use() { sum(1) + sum(1, 2) }");
  }

  public void testClassAndItsUsageAreRenamed() {
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

  public void testScriptRenameRenamesItsClass() {
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

    assertEquals("the file kept the old name", "launcher.groovy", script.getName());
    assertFileContains(user, "new launcher().run()");
  }

  public void testScriptNameThatIsNoIdentifierIsRefused() {
    PsiFile script = myFixture.addFileToProject("runner.groovy", """
      println 'hi'
      """);

    assertRefused(script, "my-runner.groovy");

    assertEquals("the rename wrote to the file", "runner.groovy", script.getName());
  }

  public void testImplicitAccessorIsRefused() {
    myFixture.addFileToProject("Bean.groovy", """
      class Bean {
        def hits
      }
      """);
    GrField hits = (GrField)findField("Bean", "hits");
    PsiMethod getter = hits.getGetters()[0];
    assertProcessor(getter, GrLightElementRenamer.class);

    HeadlessRenameResult result = HeadlessRenameProcessor.analyze(getProject(), getter, "getCount");

    HeadlessRenameResult.Failed failed = assertInstanceOf(result, HeadlessRenameResult.Failed.class);
    assertEquals(HeadlessRenameFailure.TARGET_NOT_RENAMABLE, failed.getKind());
    assertNotNull("the rename wrote to the file", findClass("Bean").findFieldByName("hits", false));
  }

  /**
   * Only {@link PropertyRenameHandler} makes a {@link PropertyForRename}, so no headless target is one.
   * This asks the processor itself for the elements it collects.
   */
  public void testPropertyCollectsItsFieldAndAccessors() {
    myFixture.addFileToProject("Bean.groovy", """
      class Bean {
        def hits
        def getHits() { hits }
        void setHits(def value) { hits = value }
      }
      """);
    PsiField hits = findField("Bean", "hits");
    PsiMethod getter = findMethod("Bean", "getHits");
    PsiMethod setter = findMethod("Bean", "setHits");
    PropertyForRename property = new PropertyForRename(List.of(hits, getter, setter), "hits", getPsiManager());
    HeadlessRenamePsiElementProcessor processor =
      (HeadlessRenamePsiElementProcessor)HeadlessRenamePsiElementProcessor.processorOf(property);
    assertInstanceOf(processor, RenameGroovyPropertyProcessor.class);

    Map<PsiElement, String> allRenames = new HashMap<>();
    allRenames.put(property, "count");
    processor.prepareRenamingHeadless(property, "count", allRenames);

    assertEquals(Map.of(hits, "count", getter, "getCount", setter, "setCount"), allRenames);
  }

  private static RenamePlan plan(PsiElement element, String newName) {
    HeadlessRenameResult result = HeadlessRenameProcessor.analyze(element.getProject(), element, newName);
    return assertInstanceOf(result, HeadlessRenameResult.Planned.class).getPlan();
  }

  private static void rename(PsiElement element, String newName) {
    assertInstanceOf(plan(element, newName).apply(), HeadlessRenameResult.Applied.class);
  }

  private static void assertRefused(PsiElement element, String newName) {
    HeadlessRenameResult result = HeadlessRenameProcessor.analyze(element.getProject(), element, newName);
    HeadlessRenameResult.Refused refused = assertInstanceOf(result, HeadlessRenameResult.Refused.class);
    assertFalse("a refusal must name its conflict", refused.getConflicts().isEmpty());
  }

  private static void assertProcessor(PsiElement element, Class<? extends RenamePsiElementProcessorBase> processorClass) {
    assertInstanceOf(HeadlessRenamePsiElementProcessor.processorOf(element), processorClass);
  }

  private static void assertFileContains(PsiFile file, String fragment) {
    assertTrue("no '" + fragment + "' in " + file.getName() + ": " + file.getText(), file.getText().contains(fragment));
  }

  private PsiField findField(String className, String name) {
    PsiField field = findClass(className).findFieldByName(name, false);
    assertNotNull("no field " + name + " in " + className, field);
    return field;
  }

  private PsiMethod findMethod(String className, String name) {
    PsiMethod[] methods = findClass(className).findMethodsByName(name, false);
    assertSize(1, methods);
    return methods[0];
  }

  private PsiClass findClass(String name) {
    PsiClass psiClass = myFixture.findClass(name);
    assertNotNull("no class " + name + " in the fixture", psiClass);
    return psiClass;
  }
}
