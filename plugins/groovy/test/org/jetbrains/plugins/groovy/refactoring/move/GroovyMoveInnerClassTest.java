// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.move.moveInner.MoveInnerProcessor;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GroovyMoveInnerClassTest extends GroovyMoveTestBase {
  private static final TestFixture<Project> project = groovyProjectFixture();

  public GroovyMoveInnerClassTest() {
    super(project, "refactoring/move/moveClass/");
  }

  @Test
  public void testAliasImportedInnerClass() {
    doTest("p2", "p1.C1.X", "Y");
  }

  @Test
  public void testStaticInnerClass() {
    doTest("p2", "p1.X.Y", "Y");
  }

  @Override
  void perform(String newPackageName, String[] classNames) {
    assertEquals(2, classNames.length, "ClassNames should contain source class name and target class name");

    String className = classNames[0];
    PsiClass psiClass = JavaPsiFacade.getInstance(groovy.get().getProject()).findClass(className, GlobalSearchScope.allScope(groovy.get().getProject()));
    assertNotNull(psiClass, "Class " + className + " not found");

    PsiPackage aPackage = JavaPsiFacade.getInstance(groovy.get().getProject()).findPackage(newPackageName);
    assertNotNull(aPackage, "Package " + newPackageName + " not found");
    final PsiDirectory[] dirs = aPackage.getDirectories();

    assertEquals(1, dirs.length);
    final PsiDirectory dir = dirs[0];
    new MoveInnerProcessor(groovy.get().getProject(), psiClass, classNames[1], false, null, dir).run();

    PsiDocumentManager.getInstance(groovy.get().getProject()).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
