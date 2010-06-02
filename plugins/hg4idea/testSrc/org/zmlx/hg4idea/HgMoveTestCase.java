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

import com.intellij.openapi.vfs.*;
import org.testng.annotations.*;

import java.io.*;

public class HgMoveTestCase extends AbstractHgTestCase {

  @Test
  public void testMoveNewFile() throws Exception {
    VirtualFile parent1 = createDirInCommand(myWorkingCopyDir, "com");
    VirtualFile file = createFileInCommand(parent1, "a.txt", "new file content");

    VirtualFile parent2 = createDirInCommand(myWorkingCopyDir, "org");
    moveFileInCommand(file, parent2);

    verify(runHgOnProjectRepo("status"), added("org", "a.txt"));
  }

  @Test
  public void testMoveUnchangedFile() throws Exception {
    VirtualFile parent1 = createDirInCommand(myWorkingCopyDir, "com");
    VirtualFile file = createFileInCommand(parent1, "a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");

    VirtualFile parent2 = createDirInCommand(myWorkingCopyDir, "org");
    moveFileInCommand(file, parent2);

    verify(runHgOnProjectRepo("status"), added("org", "a.txt"), removed("com", "a.txt"));
  }

  @Test
  public void testMoveFilesUnderFolder() throws Exception {
    VirtualFile parent1 = createDirInCommand(myWorkingCopyDir, "com");
    VirtualFile dir = createDirInCommand(parent1, "zzz");
    createFileInCommand(dir, "a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");

    VirtualFile parent2 = createDirInCommand(myWorkingCopyDir, "org");
    moveFileInCommand(dir, parent2);

    verify(runHgOnProjectRepo("status"), added("org", "zzz", "a.txt"), removed("com", "zzz", "a.txt"));
  }

  @Test
  public void testMoveUnversionedFile() throws Exception {
    VirtualFile parent1 = createDirInCommand(myWorkingCopyDir, "com");

    File unversionedFile = new File(parent1.getPath(), "a.txt");
    VirtualFile file = makeFile(unversionedFile);

    verify(runHgOnProjectRepo("status"), unknown("com", "a.txt"));

    VirtualFile parent2 = createDirInCommand(myWorkingCopyDir, "org");
    moveFileInCommand(file, parent2);

    verify(runHgOnProjectRepo("status"), unknown("org", "a.txt"));
  }

}
