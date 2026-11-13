// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class GroovyMoveClassTest extends GroovyMoveTestBase {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);

  public GroovyMoveClassTest() {
    super(project, "refactoring/move/moveClass/");
  }

  @Test
  public void testMoveMultiple1() {
    doTest("pack2", "pack1.Class1", "pack1.Class2");
  }

  @Test
  public void testSecondaryClass() {
    doTest("pack1", "pack1.Class2");
  }

  @Test
  public void testStringsAndComments() {
    doTest("pack2", "pack1.Class1");
  }

  @Test
  public void testStringsAndComments2() {
    doTest("pack2", "pack1.AClass");
  }

  @Test
  public void testLocalClass() {
    doTest("pack2", "pack1.A");
  }

  @Test
  public void testClassAndSecondary() {
    doTest("pack2", "pack1.Class1", "pack1.Class2");
  }

  @Test
  public void testIdeadev27996() {
    doTest("pack2", "pack1.X");
  }

  @Test
  public void testScript() {
    doTest("pack2", "pack1.Xx");
  }

  @Test
  public void testTwoClasses() {
    doTest("p2", "p1.C1", "p1.C2");
  }

  @Test
  public void testStaticImport() {
    doTest("p2", "p1.C1");
  }

  @Test
  public void testAliasImported() {
    doTest("p2", "p1.C1");
  }

  @Test
  public void testWithoutReference() {
    doTest("p2", "p1.X");
  }

  @Override
  void perform(String newPackageName, String[] classNames) {
    final PsiClass[] classes = new PsiClass[classNames.length];
    for (int i = 0; i < classes.length; i++) {
      String className = classNames[i];
      classes[i] = JavaPsiFacade.getInstance(groovy.get().getProject()).findClass(className, GlobalSearchScope.allScope(groovy.get().getProject()));
      assertNotNull(classes[i], "Class " + className + " not found");
    }

    PsiPackage aPackage = JavaPsiFacade.getInstance(groovy.get().getProject()).findPackage(newPackageName);
    assertNotNull(aPackage, "Package " + newPackageName + " not found");
    final PsiDirectory[] dirs = aPackage.getDirectories();

    assertEquals(1, dirs.length);
    final PsiDirectory dir = dirs[0];
    final SingleSourceRootMoveDestination moveDestination =
      new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(dir)), dir);
    new MoveClassesOrPackagesProcessor(groovy.get().getProject(), classes, moveDestination, true, true, null).run();

    PsiDocumentManager.getInstance(groovy.get().getProject()).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
