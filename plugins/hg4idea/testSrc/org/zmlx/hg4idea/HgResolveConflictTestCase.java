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
import org.zmlx.hg4idea.command.HgResolveCommand;

public class HgResolveConflictTestCase extends AbstractHgTestCase {

  public static final String BASE = "one\n" +
    "conflicting\n" +
    "two";
  
  public static final String HEAD_ONE = "one\n" +
    "conflicting in one\n" +
    "two";

  public static final String HEAD_TWO = "one\n" +
    "conflicting in two\n" +
    "two";


  @Test
  public void testMergeDataIsCorrect() throws Exception {
    createFileInCommand("conflicting", BASE);
    runHgOnProjectRepo("commit", "-m", "initial version");

    createFileInCommand("conflicting", HEAD_ONE);
    runHgOnProjectRepo("commit", "-m", "first head");

    //revert to the first commit
    runHgOnProjectRepo("up", "--clean", "0");
    createFileInCommand("conflicting", HEAD_TWO);
    runHgOnProjectRepo("commit", "-m", "second head");

    runHgOnProjectRepo("--config", "ui.merge=internal:merge", "merge");

    VirtualFile repoFile = makeFile(myProjectRepo);
    HgResolveCommand.MergeData data = new HgResolveCommand(myProject).getResolveData(repoFile, repoFile.findChild("conflicting"));

    Assert.assertEquals(data.getBase(), BASE.getBytes(),
      "The base merge information should correspond to the common ancestor");
    Assert.assertEquals(data.getLocal(), HEAD_TWO.getBytes(),
      "The local merge information should correspond to the original parent before the merge");
    Assert.assertEquals(data.getOther(), HEAD_ONE.getBytes(),
      "The 'remote' merge information should correspond to the merged in head");


  }
}
