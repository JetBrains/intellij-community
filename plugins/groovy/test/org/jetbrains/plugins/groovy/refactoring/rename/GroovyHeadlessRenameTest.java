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
    assertInstanceOf(HeadlessRenamePsiElementProcessor.processorOf(hits), HeadlessRenameGrFieldProcessor.class);

    HeadlessRenameResult result = performRename(hits, "count");

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    assertNotNull("the field kept the old name", findClass("Counter").findFieldByName("count", false));
    assertTrue("a usage kept the old name: " + reader.getText(), reader.getText().contains("counter.count + counter.getCount()"));
  }

  public void testFieldConflictWithAnExplicitGetterIsRefused() {
    myFixture.addFileToProject("Holder.groovy", """
      class Holder {
        def hits
        def getCount() { 1 }
      }
      """);
    PsiField hits = findField("Holder", "hits");

    HeadlessRenameResult result = HeadlessRenameProcessor.analyze(getProject(), hits, "count");

    HeadlessRenameResult.Refused refused = assertInstanceOf(result, HeadlessRenameResult.Refused.class);
    assertFalse("a refusal must name its conflict", refused.getConflicts().isEmpty());
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
    assertInstanceOf(HeadlessRenamePsiElementProcessor.processorOf(lock), SynchronizedRenameFieldProcessor.class);

    HeadlessRenameResult result = performRename(lock, "myLock");

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    assertTrue("the annotation does not name the new lock: " + file.getText(), file.getText().contains("@Synchronized('myLock')"));
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
    assertInstanceOf(HeadlessRenamePsiElementProcessor.processorOf(implRun), HeadlessRenameAliasImportedMethodProcessor.class);

    HeadlessRenameResult planned = HeadlessRenameProcessor.analyze(getProject(), implRun, "execute");
    HeadlessRenameResult.Planned plan = assertInstanceOf(planned, HeadlessRenameResult.Planned.class);
    assertEquals("the target was not substituted with the base method", findMethod("Base", "run"), plan.getPlan().getPrimaryElement());
    HeadlessRenameResult result = plan.getPlan().apply();

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
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
    assertInstanceOf(HeadlessRenamePsiElementProcessor.processorOf(add), RenameGrReflectedMethodProcessor.class);

    HeadlessRenameResult result = performRename(add, "sum");

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    assertTrue("a usage kept the old name: " + calc.getText(), calc.getText().contains("def use() { sum(1) + sum(1, 2) }"));
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
    assertInstanceOf(HeadlessRenamePsiElementProcessor.processorOf(widget), RenameAliasImportedClassProcessor.class);

    HeadlessRenameResult result = performRename(widget, "Gadget");

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    assertTrue("a usage kept the old name: " + holder.getText(), holder.getText().contains("Gadget widget = new Gadget()"));
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
    assertInstanceOf(HeadlessRenamePsiElementProcessor.processorOf(script), RenameGroovyScriptProcessor.class);

    HeadlessRenameResult result = performRename(script, "launcher.groovy");

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    assertEquals("the file kept the old name", "launcher.groovy", script.getName());
    assertTrue("a usage of the script class kept the old name: " + user.getText(), user.getText().contains("new launcher().run()"));
  }

  public void testScriptNameThatIsNoIdentifierIsRefused() {
    PsiFile script = myFixture.addFileToProject("runner.groovy", """
      println 'hi'
      """);

    HeadlessRenameResult result = HeadlessRenameProcessor.analyze(getProject(), script, "my-runner.groovy");

    HeadlessRenameResult.Refused refused = assertInstanceOf(result, HeadlessRenameResult.Refused.class);
    assertFalse("a refusal must name its conflict", refused.getConflicts().isEmpty());
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
    assertInstanceOf(HeadlessRenamePsiElementProcessor.processorOf(getter), GrLightElementRenamer.class);

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

  private static HeadlessRenameResult performRename(PsiElement element, String newName) {
    HeadlessRenameResult planned = HeadlessRenameProcessor.analyze(element.getProject(), element, newName);
    HeadlessRenameResult.Planned plan = assertInstanceOf(planned, HeadlessRenameResult.Planned.class);
    return plan.getPlan().apply();
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
