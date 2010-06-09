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
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;

public class HgDeleteTestCase extends HgTestCase {

  @Test
  public void testDeleteUnmodifiedFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");
    deleteFileInCommand(file);
    verify(runHgOnProjectRepo("status"), removed("a.txt"));
  }

  @Test
  public void testDeleteUnversionedFile() throws Exception {
    VirtualFile file = makeFile(new File(myWorkingCopyDir.getPath(), "a.txt"));
    verify(runHgOnProjectRepo("status"), unknown("a.txt"));
    deleteFileInCommand(file);
    Assert.assertFalse(file.exists());
  }

  @Test
  public void testDeleteNewFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    deleteFileInCommand(file);
    Assert.assertFalse(file.exists());
  }

  @Test
  public void testDeleteModifiedFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");
    editFileInCommand(myProject, file, "even newer content");
    verify(runHgOnProjectRepo("status"), modified("a.txt"));
    deleteFileInCommand(file);
    verify(runHgOnProjectRepo("status"), removed("a.txt"));
  }

  @Test
  public void testDeleteDirWithFiles() throws Exception {
    VirtualFile parent = createDirInCommand(myWorkingCopyDir, "com");
    createFileInCommand(parent, "a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");
    deleteFileInCommand(parent);
    verify(runHgOnProjectRepo("status"), removed("com", "a.txt"));
  }

}
