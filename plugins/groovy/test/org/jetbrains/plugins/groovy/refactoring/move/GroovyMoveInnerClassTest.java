// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.move.moveInner.MoveInnerProcessor;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class GroovyMoveInnerClassTest extends GroovyMoveTestBase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/move/moveClass/";
  }

  public void testAliasImportedInnerClass() {
    doTest("p2", "p1.C1.X", "Y");
  }

  public void testStaticInnerClass() {
    doTest("p2", "p1.X.Y", "Y");
  }

  @Override
  void perform(String newPackageName, String[] classNames) {
    assertEquals("ClassNames should contain source class name and target class name", 2, classNames.length);

    String className = classNames[0];
    PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject()));
    assertNotNull("Class " + className + " not found", psiClass);

    PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(newPackageName);
    assertNotNull("Package " + newPackageName + " not found", aPackage);
    final PsiDirectory[] dirs = aPackage.getDirectories();

    assertEquals(1, dirs.length);
    final PsiDirectory dir = dirs[0];
    new MoveInnerProcessor(getProject(), psiClass, classNames[1], false, null, dir).run();

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
