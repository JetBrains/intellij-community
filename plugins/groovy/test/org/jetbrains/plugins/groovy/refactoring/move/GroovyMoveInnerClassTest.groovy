// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.refactoring.move

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.move.moveInner.MoveInnerProcessor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class GroovyMoveInnerClassTest extends GroovyMoveTestBase {
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/move/moveClass/"
  }

  void testAliasImportedInnerClass() {
    doTest("p2", "p1.C1.X", "Y")
  }

  void testStaticInnerClass() {
    doTest("p2", "p1.X.Y", "Y")
  }

  boolean perform(VirtualFile root, String newPackageName, String... classNames) {
    assertEquals("ClassNames should contain source class name and target class name", 2, classNames.length)

    String className = classNames[0]
    PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject()))
    assertNotNull("Class " + className + " not found", psiClass)

    PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(newPackageName)
    assertNotNull("Package " + newPackageName + " not found", aPackage)
    final PsiDirectory[] dirs = aPackage.getDirectories()

    final PsiDirectory dir = dirs[dirs.length - 1]
    new MoveInnerProcessor(getProject(), psiClass, classNames[1], false, null, dir).run()

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    FileDocumentManager.getInstance().saveAllDocuments()

    return true
  }
}
