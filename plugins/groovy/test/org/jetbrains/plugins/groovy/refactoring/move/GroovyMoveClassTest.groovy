/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.refactoring.move

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.registry.Registry
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

  static class BranchTest extends GroovyMoveClassTest {
    @Override
    protected void setUp() throws Exception {
      super.setUp();
      Registry.get("run.refactorings.in.model.branch").setValue(true, getTestRootDisposable());
    }
  }

}
