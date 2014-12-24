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
package hg4idea.test.commit;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.command.HgCommitCommand;
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryImpl;

import java.util.List;

import static com.intellij.openapi.vcs.Executor.*;
import static hg4idea.test.HgExecutor.hg;

/**
 * @author Nadya Zabrodina
 */
public class HgCommitTest extends HgPlatformTest {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    cd(myRepository);
    String abFile = "AB.txt";
    touch(abFile, "base");
    hg("add " + abFile);
    hg("commit -m amend");
    echo(abFile, "text for amend");
  }

  public void testAmendCommit() throws HgCommandException, VcsException {
    String changedCommit = "anotherCommit";
    HgLogCommand logCommand = new HgLogCommand(myProject);
    logCommand.setLogFile(false);
    HgFile hgFile = new HgFile(myRepository, VfsUtilCore.virtualToIoFile(myRepository));
    List<HgFileRevision> revisions = logCommand.execute(hgFile, -1, false);
    HgRepository hgRepo = HgRepositoryImpl.getInstance(myRepository, myProject, myProject);
    HgCommitCommand commit = new HgCommitCommand(myProject, hgRepo, changedCommit, true);
    commit.execute();
    List<HgFileRevision> revisionsAfterAmendCommit = logCommand.execute(hgFile, -1, false);
    assertTrue(revisions.size() == revisionsAfterAmendCommit.size());
    assertEquals(revisionsAfterAmendCommit.get(0).getCommitMessage(), changedCommit);
  }
}
