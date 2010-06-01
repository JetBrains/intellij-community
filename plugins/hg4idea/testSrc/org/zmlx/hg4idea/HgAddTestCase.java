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

public class HgAddTestCase extends HgTestCase {

  @Test
  public void testAddFile() throws Exception {
    createFileInCommand("a.txt", "new file content");
    verify(runHgOnProjectRepo("status"), added("a.txt"));
  }

  @Test
  public void testAddFileInDirectory() throws Exception {
    VirtualFile parent = createDirInCommand(myWorkingCopyDir, "com");
    createFileInCommand(parent, "a.txt", "new file content");
    verify(runHgOnProjectRepo("status"), added("com", "a.txt"));
  }
}
