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
import org.jetbrains.plugins.groovy.util.TestUtils;


public class GroovyMoveClassTest extends GroovyMoveTestBase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/move/moveClass/";
  }

  public void testMoveMultiple1() {
    doTest("pack2", "pack1.Class1", "pack1.Class2");
  }

  public void testSecondaryClass() {
    doTest("pack1", "pack1.Class2");
  }

  public void testStringsAndComments() {
    doTest("pack2", "pack1.Class1");
  }

  public void testStringsAndComments2() {
    doTest("pack2", "pack1.AClass");
  }

  public void testLocalClass() {
    doTest("pack2", "pack1.A");
  }

  public void testClassAndSecondary() {
    doTest("pack2", "pack1.Class1", "pack1.Class2");
  }

  public void testIdeadev27996() {
    doTest("pack2", "pack1.X");
  }

  public void testScript() {
    doTest("pack2", "pack1.Xx");
  }

  public void testTwoClasses() {
    doTest("p2", "p1.C1", "p1.C2");
  }

  public void testStaticImport() {
    doTest("p2", "p1.C1");
  }

  public void testAliasImported() {
    doTest("p2", "p1.C1");
  }

  public void testWithoutReference() {
    doTest("p2", "p1.X");
  }

  @Override
  void perform(String newPackageName, String[] classNames) {
    final PsiClass[] classes = new PsiClass[classNames.length];
    for (int i = 0; i < classes.length; i++) {
      String className = classNames[i];
      classes[i] = JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject()));
      assertNotNull("Class " + className + " not found", classes[i]);
    }

    PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(newPackageName);
    assertNotNull("Package " + newPackageName + " not found", aPackage);
    final PsiDirectory[] dirs = aPackage.getDirectories();

    assertEquals(1, dirs.length);
    final PsiDirectory dir = dirs[0];
    final SingleSourceRootMoveDestination moveDestination =
      new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(dir)), dir);
    new MoveClassesOrPackagesProcessor(getProject(), classes, moveDestination, true, true, null).run();

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
