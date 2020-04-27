/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
