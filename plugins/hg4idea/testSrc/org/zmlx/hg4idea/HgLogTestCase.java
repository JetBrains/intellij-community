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

import org.testng.annotations.*;
import org.zmlx.hg4idea.command.*;

import java.util.*;

import static org.testng.Assert.*;

public class HgLogTestCase extends HgTestCase {
  
  @Test
  public void testCommitMessagesWithMultipleLines() throws Exception {
    fillFile(projectRepo, new String[]{"file.txt"}, "initial contents");
    runHgOnProjectRepo("add", ".");
    runHgOnProjectRepo("commit", "-m", "initial\ncontents");

    fillFile(projectRepo, new String[]{"file.txt"}, "updated contents");
    runHgOnProjectRepo("commit", "-m", "updated\ncontents");

    List<HgFileRevision> fileLog = new HgLogCommand(myProject).execute(getHgFile("file.txt"), 10, false);
    assertEquals(fileLog.size(), 2, "The file history should show two entries");
  }
}
