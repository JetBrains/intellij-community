/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package hg4idea.test.merge;

import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.command.HgRevertCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.io.File;
import java.util.Collections;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.touch;
import static hg4idea.test.HgExecutor.hg;
import static hg4idea.test.HgExecutor.hgMergeWith;

public class HgRevertUncommittedMergeTest extends HgPlatformTest {

  public void testRevertAfterMerge() {
    cd(myRepository);
    hg("branch branchA");
    String aFile = "A.txt";
    touch(aFile, "a");
    hg("add " + aFile);
    hg("commit -m 'create file in branchA' ");
    hg("up default");
    touch(aFile, "default");
    hg("add " + aFile);
    hg("commit -m 'create file in default branch'");
    hgMergeWith("branchA");
    HgCommandResult revertResult = new HgRevertCommand(myProject)
      .execute(myRepository, Collections.singleton(new File(myRepository.getPath(), aFile).getAbsolutePath()), null, false);
    assertTrue(HgErrorUtil.hasUncommittedChangesConflict(revertResult));
  }
}
