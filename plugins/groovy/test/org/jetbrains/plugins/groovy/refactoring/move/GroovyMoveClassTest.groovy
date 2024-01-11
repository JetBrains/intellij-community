// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.refactoring.move

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
@CompileStatic
class GroovyMoveClassTest extends GroovyMoveTestBase {
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/move/moveClass/"
  }

  void testMoveMultiple1() throws Exception {
    doTest("pack2", "pack1.Class1", "pack1.Class2")
  }

  void testSecondaryClass() throws Exception {
    doTest("pack1", "pack1.Class2")
  }

  void testStringsAndComments() throws Exception {
    doTest("pack2", "pack1.Class1")
  }

  void testStringsAndComments2() throws Exception {
    doTest("pack2", "pack1.AClass")
  }

  void testLocalClass() throws Exception {
    doTest("pack2", "pack1.A")
  }

  void testClassAndSecondary() throws Exception {
    doTest("pack2", "pack1.Class1", "pack1.Class2")
  }

  void testIdeadev27996() throws Exception {
    doTest("pack2", "pack1.X")
  }

  void testScript() throws Exception {
    doTest("pack2", "pack1.Xx")
  }

  void testTwoClasses() {
    doTest("p2", "p1.C1", "p1.C2")
  }

  void testStaticImport() {
    doTest("p2", "p1.C1")
  }

  void testAliasImported() {
    doTest("p2", "p1.C1")
  }

  void testWithoutReference() {
    doTest("p2", "p1.X")
  }

  boolean perform(VirtualFile root, String newPackageName, String... classNames) {
    final PsiClass[] classes = new PsiClass[classNames.length]
    for (int i = 0; i < classes.length; i++) {
      String className = classNames[i]
      classes[i] = JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject()))
      assertNotNull("Class " + className + " not found", classes[i])
    }

    PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(newPackageName)
    assertNotNull("Package " + newPackageName + " not found", aPackage)
    final PsiDirectory[] dirs = aPackage.getDirectories()

    final PsiDirectory dir = dirs[dirs.length - 1]
    final SingleSourceRootMoveDestination moveDestination =
      new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(dir)), dir)
    new MoveClassesOrPackagesProcessor(getProject(), classes, moveDestination, true, true, null).run()

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    FileDocumentManager.getInstance().saveAllDocuments()

    return true
  }
}
