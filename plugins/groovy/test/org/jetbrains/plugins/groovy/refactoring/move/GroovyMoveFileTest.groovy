/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.move;


import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
public class GroovyMoveFileTest extends GroovyMoveTestBase {
  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}refactoring/move/moveFile/";
  }

  public void testMoveJavaGroovyText() {
    doTest 'pack2', 'A.groovy', 'B.java', 'C.txt'
  }

  @Override
  boolean perform(VirtualFile root, String moveTo, String... names) {
    def pack1 = root.findChild('pack1')
    PsiFile[] files = myFixture.psiManager.findDirectory(pack1).files.findAll {file -> names.find {name -> name == file.name}}
    def dir = myFixture.psiManager.findDirectory(root.findChild(moveTo))

    new MoveFilesOrDirectoriesProcessor(myFixture.project, files, dir, false, false, false, null, null).run()
    return true
  }
}
