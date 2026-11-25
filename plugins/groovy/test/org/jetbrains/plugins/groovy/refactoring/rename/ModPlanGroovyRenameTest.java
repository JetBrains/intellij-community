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
import com.intellij.refactoring.modplan.ModPlan;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.rename.GrLightElementRenamer.ModPlanGrLightElementRenameProvider;
import org.jetbrains.plugins.groovy.refactoring.rename.RenameAliasImportedClassProcessor.ModPlanGrClassRenameProvider;
import org.jetbrains.plugins.groovy.refactoring.rename.RenameAliasImportedMethodProcessor.ModPlanGrMethodRenameProvider;
import org.jetbrains.plugins.groovy.refactoring.rename.RenameGroovyPropertyProcessor.ModPlanGroovyPropertyRenameProvider;
import org.jetbrains.plugins.groovy.refactoring.rename.RenameGroovyScriptProcessor.ModPlanGroovyScriptRenameProvider;
import org.jetbrains.plugins.groovy.transformations.impl.synch.ModPlanSynchronizedFieldRenameProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.jetbrains.plugins.groovy.refactoring.rename.GroovyModPlanRenameTests.assertOwner;
import static org.jetbrains.plugins.groovy.refactoring.rename.GroovyModPlanRenameTests.plan;
import static org.jetbrains.plugins.groovy.refactoring.rename.GroovyModPlanRenameTests.planWithNoSubstitution;
import static org.jetbrains.plugins.groovy.refactoring.rename.GroovyModPlanRenameTests.rename;
import static org.jetbrains.plugins.groovy.refactoring.rename.GroovyModPlanRenameTests.renameWithNoSubstitution;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ModPlanGroovyRenameTest {
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
    assertOwner(hits, ModPlanGrFieldRenameProvider.class);

    rename(hits, "count");

    ReadAction.runBlocking(() -> {
      assertNotNull(findClass("Counter").findFieldByName("count", false), "the field kept the old name");
      assertFileContains(reader, "counter.count + counter.getCount()");
    });
  }

  @Test
  public void testFieldConflictWithAnExplicitGetterNeedsConfirmation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Holder.groovy", """
      class Holder {
        def hits
        def getCount() { 1 }
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiField hits = findField("Holder", "hits");

      assertConflicts(hits, "count");

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
    assertOwner(lock, ModPlanSynchronizedFieldRenameProvider.class);

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
    assertOwner(implRun, ModPlanGrMethodRenameProvider.class);

    rename(implRun, "execute");

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
    assertOwner(add, ModPlanGrReflectedMethodRenameProvider.class);

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
    assertOwner(widget, ModPlanGrClassRenameProvider.class);

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
    assertOwner(script, ModPlanGroovyScriptRenameProvider.class);

    rename(script, "launcher.groovy");

    assertEquals("launcher.groovy", script.getName(), "the file kept the old name");
    assertFileContains(user, "new launcher().run()");
  }

  @Test
  public void testScriptNameThatIsNoIdentifierNeedsConfirmation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile script = myFixture.addFileToProject("runner.groovy", """
      println 'hi'
      """);

    ReadAction.runBlocking(() -> assertConflicts(script, "my-runner.groovy"));

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
      assertOwner(getter, ModPlanGrLightElementRenameProvider.class);

      ModPlan.RefusedPlan refused = assertInstanceOf(ModPlan.RefusedPlan.class, plan(getter, "getCount"));
      assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refused.getReason());
      assertNotNull(findClass("Bean").findFieldByName("hits", false), "the rename wrote to the file");
    });
  }

  /** A caller that does not substitute gets a refusal too: no edit can write an implicit accessor. */
  @Test
  public void testImplicitAccessorWithNoSubstitutionIsRefused() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Bean.groovy", """
      class Bean {
        def hits
      }
      """);
    ReadAction.runBlocking(() -> {
      GrField hits = (GrField)findField("Bean", "hits");
      PsiMethod getter = hits.getGetters()[0];

      ModPlan.RefusedPlan refused = assertInstanceOf(ModPlan.RefusedPlan.class, planWithNoSubstitution(getter, "getCount"));
      assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refused.getReason());
    });
  }

  /** The rename of a script class with no substitution moves its file, as {@code GroovyScriptClass.setName} does. */
  @Test
  public void testScriptClassWithNoSubstitutionMovesItsFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFile script = (GroovyFile)myFixture.addFileToProject("runner.groovy", """
      println 'hi'
      """);
    PsiFile user = myFixture.addFileToProject("User.groovy", """
      class User {
        def start() { new runner().run() }
      }
      """);
    PsiClass scriptClass = ReadAction.computeBlocking(() -> script.getScriptClass());
    assertNotNull(scriptClass, "no script class");
    assertOwner(scriptClass, ModPlanGrLightElementRenameProvider.class);

    renameWithNoSubstitution(scriptClass, "launcher");

    assertEquals("launcher.groovy", script.getName(), "the file kept the old name");
    assertFileContains(user, "new launcher().run()");
  }

  /** A property access to a renamed getter gets the name of the new property, not the name of the getter. */
  @Test
  public void testPropertyAccessOfARenamedGetterGetsThePropertyName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Foo.groovy", """
      class Foo {
        def getFoo() { 2 }
      }
      """);
    PsiFile user = myFixture.addFileToProject("user.groovy", """
      print new Foo().foo
      """);
    PsiMethod getFoo = findMethod("Foo", "getFoo");
    assertOwner(getFoo, ModPlanGrMethodRenameProvider.class);

    rename(getFoo, "getBar");

    assertFileContains(user, "print new Foo().bar");
  }

  /**
   * The Java provider owns a Java class. A Groovy reference through an import alias keeps its name, and the import stays. The legacy
   * rename gives this result only if it binds the import first, so the legacy test would depend on the order of the usages.
   */
  @Test
  public void testAliasImportOfARenamedJavaClassStays() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("p/Widget.java", """
      package p;
      public class Widget {}
      """);
    PsiFile user = myFixture.addFileToProject("user.groovy", """
      import p.Widget as W
      print new W()
      """);

    rename(findClass("p.Widget"), "Gadget");

    assertFileContains(user, """
      import p.Gadget as W
      print new W()
      """);
  }

  @Test
  public void testJavaImportOfARenamedGroovyClassGetsTheNewName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("p/Widget.groovy", """
      package p
      class Widget {}
      """);
    PsiFile holder = myFixture.addFileToProject("Holder.java", """
      import p.Widget;
      class Holder {
        Widget widget;
      }
      """);

    rename(findClass("p.Widget"), "Gadget");

    assertFileContains(holder, "import p.Gadget;");
    assertFileContains(holder, "Gadget widget;");
  }

  /** Only {@link PropertyRenameHandler} makes a {@link PropertyForRename}. Its rename renames the field and the accessors. */
  @Test
  public void testPropertyRenamesItsFieldAndAccessors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile bean = myFixture.addFileToProject("Bean.groovy", """
      class Bean {
        def hits
        def getHits() { hits }
        void setHits(def value) { hits = value }
      }
      """);
    PropertyForRename property = ReadAction.computeBlocking(() -> {
      PsiField hits = findField("Bean", "hits");
      PsiMethod getter = findMethod("Bean", "getHits");
      PsiMethod setter = findMethod("Bean", "setHits");
      return new PropertyForRename(List.of(hits, getter, setter), "hits", PsiManager.getInstance(myFixture.getProject()));
    });
    assertOwner(property, ModPlanGroovyPropertyRenameProvider.class);

    rename(property, "count");

    assertFileContains(bean, """
      class Bean {
        def count
        def getCount() { count }
        void setCount(def value) { count = value }
      }
      """);
  }

  /** Asserts that the plan of the rename has a conflict that needs a confirmation, as the legacy rename shows a conflict dialog. */
  private static void assertConflicts(PsiElement element, String newName) {
    ModPlan.AcceptedPlan accepted = assertInstanceOf(ModPlan.AcceptedPlan.class, plan(element, newName));
    assertTrue(accepted.getConflicts().needsConfirmation(), "a conflict must need a confirmation");
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
