/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.repo;

import com.intellij.dvcs.repo.Repository;
import git4idea.GitLocalBranch;
import git4idea.test.GitSingleRepoTest;

import java.io.File;

import static git4idea.test.GitExecutor.git;
import static git4idea.test.GitScenarios.commit;
import static git4idea.test.GitScenarios.conflict;
/**
 * {@link GitRepositoryReaderTest} reads information from the pre-created .git directory from a real project.
 * This one, on the other hand, operates on a live Git repository, putting it to various situations and checking the results.
 */
public class GitRepositoryReaderNewTest extends GitSingleRepoTest {

  // inspired by IDEA-93806
  public void test_rebase_with_conflicts_while_being_on_detached_HEAD() {
    conflict(myRepo, "feature");
    commit(myRepo);
    commit(myRepo);
    git("checkout HEAD^");
    git("rebase feature", true);

    File gitDir = new File(myRepo.getRoot().getPath(), ".git");
    GitRepositoryReader reader = new GitRepositoryReader(gitDir);
    GitLocalBranch branch = reader.readCurrentBranch();
    Repository.State state = reader.readState();
    assertNull("Current branch can't be identified for this case", branch);
    assertEquals("State value is incorrect", Repository.State.REBASING, state);
  }

}
