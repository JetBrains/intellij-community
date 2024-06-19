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
package org.jetbrains.plugins.groovy.refactoring.move;


import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Max Medvedev
 */
public class GroovyMoveFileTest extends GroovyMoveTestBase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/move/moveFile/";
  }

  public void testMoveJavaGroovyText() {
    doTest("pack2", "A.groovy", "B.java", "C.txt");
  }

  @Override
  void perform(String newPackageName, String[] names) {
    VirtualFile pack1 = myFixture.findFileInTempDir("pack1");
    PsiFile[] rawFiles = myFixture.getPsiManager().findDirectory(pack1).getFiles();
    PsiFile[] files =
      ContainerUtil.filter(rawFiles, file -> ContainerUtil.or(names, name -> name.equals(file.getName()))).toArray(PsiFile.EMPTY_ARRAY);
    PsiDirectory dir = myFixture.getPsiManager().findDirectory(myFixture.findFileInTempDir(newPackageName));

    new MoveFilesOrDirectoriesProcessor(myFixture.getProject(), files, dir, false, false, false, null, null).run();
  }
}
