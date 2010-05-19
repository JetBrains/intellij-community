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
package org.zmlx.hg4idea;

import com.intellij.openapi.vfs.VirtualFile;
import org.testng.annotations.Test;

import java.io.File;

public class HgCopyTestCase extends HgTestCase {

  @Test
  public void testCopyUnmodifiedFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    runHg("commit", "-m", "added file");
    copyFileInCommand(file, "b.txt");
    verify(runHg("status"), "A b.txt");
  }

  @Test
  public void testCopyModifiedFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    runHg("commit", "-m", "added file");
    editFileInCommand(myProject, file, "newer content");
    verify(runHg("status"), "M a.txt");
    copyFileInCommand(file, "b.txt");
    verify(runHg("status"), "M a.txt", "A b.txt");
  }

  @Test
  public void testCopyUnversionedFile() throws Exception {
    VirtualFile file = makeFile(new File(myWorkingCopyDir.getPath(), "a.txt"));
    copyFileInCommand(file, "b.txt");
    verify(runHg("status"), "? a.txt", "? b.txt");
  }

  @Test
  public void testCopyCopiedFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    runHg("commit", "-m", "added file");
    copyFileInCommand(file, "b.txt");
    copyFileInCommand(file, "c.txt");
    verify(runHg("status"), "A b.txt", "A c.txt");
  }

  @Test
  public void testCopyDirWithFiles() throws Exception {
    VirtualFile parent = createDirInCommand(myWorkingCopyDir, "com");
    createFileInCommand(parent, "a.txt", "new file content");
    runHg("commit", "-m", "added file");
    copyFileInCommand(parent, "org");
    verify(runHg("status"), "A org/a.txt");
  }

}
