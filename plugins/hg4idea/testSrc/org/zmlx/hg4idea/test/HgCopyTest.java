// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.test;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class HgCopyTest extends HgSingleUserTest {
  @Test
  public void testCopyUnmodifiedFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");
    copyFileInCommand(file, "b.txt");
    verifyStatus(HgTestOutputParser.added("b.txt"));
  }

  @Test
  public void testCopyModifiedFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");
    VcsTestUtil.editFileInCommand(myProject, file, "newer content");
    verifyStatus(HgTestOutputParser.modified("a.txt"));
    copyFileInCommand(file, "b.txt");
    verifyStatus(HgTestOutputParser.modified("a.txt"), HgTestOutputParser.added("b.txt"));
  }

  @Test
  public void testCopyUnversionedFile() throws Exception {
    VirtualFile file = makeFile(new File(myWorkingCopyDir.getPath(), "a.txt"));
    copyFileInCommand(file, "b.txt");
    verifyStatus(HgTestOutputParser.added("b.txt"), HgTestOutputParser.unknown("a.txt"));
  }

  @Test
  public void testCopyCopiedFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");
    copyFileInCommand(file, "b.txt");
    copyFileInCommand(file, "c.txt");
    verifyStatus(HgTestOutputParser.added("b.txt"), HgTestOutputParser.added("c.txt"));
  }

  @Test
  public void testCopyDirWithFiles() throws Exception {
    VirtualFile parent = createDirInCommand(myWorkingCopyDir, "com");
    createFileInCommand(parent, "a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");
    copyDir(parent, "org");
    verifyStatus(HgTestOutputParser.added("org", "a.txt"));
  }


  private void copyDir(@NotNull VirtualFile vDir, @NotNull String newName) throws IOException {
    WriteCommandAction.writeCommandAction(myProject).run(() -> {
      PsiDirectory psiDirectory = PsiManager.getInstance(myProject).findDirectory(vDir);
      CopyFilesOrDirectoriesHandler.copyToDirectory(psiDirectory, newName, psiDirectory.getParentDirectory());
    });
  }
}
