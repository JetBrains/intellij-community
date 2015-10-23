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
import com.intellij.dvcs.push.PushSupport;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import git4idea.*;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.test.GitPlatformTest;
import git4idea.test.GitTestUtil;
import git4idea.test.MockVcsHelper;
import git4idea.test.TestDialogHandler;
import git4idea.update.GitUpdateResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import static git4idea.test.GitExecutor.cd;
import static git4idea.test.GitExecutor.git;

abstract class GitPushOperationBaseTest extends GitPlatformTest {

  protected GitPushSupport myPushSupport;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myPushSupport = findGitPushSupport();

    GitTestUtil.overrideService(myProject, AbstractVcsHelper.class, MockVcsHelper.class);
  }

  @Override
  protected void refresh() {
    super.refresh();
    myGitRepositoryManager.updateAllRepositories();
  }

  @NotNull
  @Override
  protected Collection<String> getDebugLogCategories() {
    return Collections.singletonList("#" + GitPushOperation.class.getName());
  }

  @NotNull
  protected Trinity<GitRepository, File, File> setupRepositories(String repoRoot, String parentName, String broName) {
    File parentRepo = createParentRepo(parentName);
    File broRepo = createBroRepo(broName, parentRepo);

    GitRepository repository = GitTestUtil.createRepository(myProject, repoRoot);
    cd(repository);
    git("remote add origin " + parentRepo.getPath());
    git("push --set-upstream origin master:master");

    cd(broRepo.getPath());
    git("pull");

    return Trinity.create(repository, parentRepo, broRepo);
  }

  @NotNull
  private File createParentRepo(@NotNull String parentName) {
    cd(myTestRoot);
    git("init --bare " + parentName + ".git");
    return new File(myTestRoot, parentName + ".git");
  }

  @NotNull
  private File createBroRepo(@NotNull String broName, @NotNull File parentRepo) {
    cd(myTestRoot);
    git("clone " + parentRepo.getName() + " " + broName);
    return new File(myTestRoot, broName);
  }

  @NotNull
  protected static PushSpec<GitPushSource, GitPushTarget> makePushSpec(@NotNull GitRepository repository,
                                                                       @NotNull String from,
                                                                       @NotNull String to) {
    GitLocalBranch source = repository.getBranches().findLocalBranch(from);
    assertNotNull(source);
    GitRemoteBranch target = (GitRemoteBranch)repository.getBranches().findBranchByName(to);
    boolean newBranch;
    if (target == null) {
      int firstSlash = to.indexOf('/');
      GitRemote remote = GitUtil.findRemoteByName(repository, to.substring(0, firstSlash));
      assertNotNull(remote);
      target = new GitStandardRemoteBranch(remote, to.substring(firstSlash + 1), GitBranch.DUMMY_HASH);
      newBranch = true;
    }
    else {
      newBranch = false;
    }
    return new PushSpec<GitPushSource, GitPushTarget>(GitPushSource.create(source), new GitPushTarget(target, newBranch));
  }

  protected void agreeToUpdate(final int exitCode) {
    myDialogManager.registerDialogHandler(GitRejectedPushUpdateDialog.class, new TestDialogHandler<GitRejectedPushUpdateDialog>() {
      @Override
      public int handleDialog(GitRejectedPushUpdateDialog dialog) {
        return exitCode;
      }
    });
  }

  protected void assertResult(@NotNull GitPushRepoResult.Type type, int pushedCommits, @NotNull String from, @NotNull String to,
                              @Nullable GitUpdateResult updateResult,
                              @NotNull GitPushRepoResult actualResult) {
    String message = "Result is incorrect: " + actualResult;
    assertEquals(message, type, actualResult.getType());
    assertEquals(message, pushedCommits, actualResult.getNumberOfPushedCommits());
    assertEquals(message, GitBranch.REFS_HEADS_PREFIX + from, actualResult.getSourceBranch());
    assertEquals(message, GitBranch.REFS_REMOTES_PREFIX + to, actualResult.getTargetBranch());
    assertEquals(message, updateResult, actualResult.getUpdateResult());
  }


  @NotNull
  private GitPushSupport findGitPushSupport() {
    return (GitPushSupport)ObjectUtils.assertNotNull(ContainerUtil.find(Extensions.getExtensions(PushSupport.PUSH_SUPPORT_EP, myProject),
                                                                        new Condition<PushSupport<?, ?, ?>>() {
                                                                          @Override
                                                                          public boolean value(PushSupport<?, ?, ?> support) {
                                                                            return support instanceof GitPushSupport;
                                                                          }
                                                                        }));
  }
}
