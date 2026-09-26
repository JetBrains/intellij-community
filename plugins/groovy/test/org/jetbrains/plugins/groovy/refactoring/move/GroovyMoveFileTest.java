// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
