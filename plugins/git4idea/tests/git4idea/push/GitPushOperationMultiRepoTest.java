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
package git4idea.push;

import com.intellij.dvcs.push.PushSpec;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Trinity;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitImpl;
import git4idea.commands.GitLineHandlerListener;
import git4idea.repo.GitRepository;
import git4idea.test.GitTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static git4idea.test.GitExecutor.cd;

public class GitPushOperationMultiRepoTest extends GitPushOperationBaseTest {

  private GitRepository myCommunity;
  private GitRepository myRepository;

  @Override
  protected void setUp() throws Exception {
    try {
      super.setUp();
    }
    catch (Exception e) {
      super.tearDown();
      throw e;
    }

    try {
      Trinity<GitRepository, File, File> mainRepo = setupRepositories(myProjectPath, "parent", "bro");
      myRepository = mainRepo.first;

      File community = new File(myProjectPath, "community");
      assertTrue(community.mkdir());
      Trinity<GitRepository, File, File> enclosingRepo = setupRepositories(community.getPath(),
                                                                           "community_parent", "community_bro");
      myCommunity = enclosingRepo.first;

      cd(myProjectPath);
      refresh();
    }
    catch (Exception e) {
      tearDown();
      throw e;
    }
  }

  private static class FailingPushGit extends GitImpl {
    private Condition<GitRepository> myPushShouldFail;

    @NotNull
    @Override
    public GitCommandResult push(@NotNull GitRepository repository,
                                 @NotNull GitLocalBranch source,
                                 @NotNull GitRemoteBranch target,
                                 boolean force,
                                 boolean updateTracking,
                                 @Nullable String tagMode,
                                 GitLineHandlerListener... listeners) {
      if (myPushShouldFail.value(repository)) {
        return new GitCommandResult(false, 128, Arrays.asList("Failed to push to " + target.getName()),
                                    Collections.<String>emptyList(), null);
      }
      return super.push(repository, source, target, force, updateTracking, tagMode, listeners);
    }
  }

  public void test_try_push_from_all_roots_even_if_one_fails() throws IOException {
    FailingPushGit failingPushGit = GitTestUtil.overrideService(Git.class, FailingPushGit.class);
    // fail in the first repo
    failingPushGit.myPushShouldFail = new Condition<GitRepository>() {
      @Override
      public boolean value(GitRepository repository) {
        return repository.equals(myRepository);
      }
    };

    cd(myRepository);
    makeCommit("file.txt");
    cd(myCommunity);
    makeCommit("com.txt");

    PushSpec<GitPushSource, GitPushTarget> spec1 = makePushSpec(myRepository, "master", "origin/master");
    PushSpec<GitPushSource, GitPushTarget> spec2 = makePushSpec(myCommunity, "master", "origin/master");
    Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> map = ContainerUtil.newHashMap();
    map.put(myRepository, spec1);
    map.put(myCommunity, spec2);
    GitPushResult result = new GitPushOperation(myProject, map, null, false).execute();

    GitPushRepoResult result1 = result.getResults().get(myRepository);
    GitPushRepoResult result2 = result.getResults().get(myCommunity);

    assertResult(GitPushRepoResult.Type.ERROR, -1, "master", "origin/master", null, result1);
    assertEquals("Error text is incorrect", "Failed to push to origin/master", result1.getError());
    assertResult(GitPushRepoResult.Type.SUCCESS, 1, "master", "origin/master", null, result2);
  }

}
