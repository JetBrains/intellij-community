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

import com.intellij.dvcs.push.PushTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitStandardRemoteBranch;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.util.Collection;

import static git4idea.GitUtil.findRemoteBranch;

public class GitPushTarget implements PushTarget {

  private static final Logger LOG = Logger.getInstance(GitPushTarget.class);

  @NotNull private final GitRemoteBranch myRemoteBranch;
  private final boolean myIsNewBranchCreated;

  public GitPushTarget(@NotNull GitRemoteBranch remoteBranch, boolean isNewBranchCreated) {
    myRemoteBranch = remoteBranch;
    myIsNewBranchCreated = isNewBranchCreated;
  }

  @NotNull
  public GitRemoteBranch getBranch() {
    return myRemoteBranch;
  }

  @Override
  public boolean hasSomethingToPush() {
    return isNewBranchCreated();
  }

  @NotNull
  @Override
  public String getPresentation() {
    return myRemoteBranch.getNameForRemoteOperations();
  }

  public boolean isNewBranchCreated() {
    return myIsNewBranchCreated;
  }

  @NotNull
  public static GitPushTarget parse(@NotNull GitRepository repository, @Nullable String remoteName, @NotNull String branchName) throws
                                                                                                                        ParseException {
    if (remoteName == null) {
      throw new ParseException("No remotes defined", -1);
    }

    if (!GitRefNameValidator.getInstance().checkInput(branchName)) {
      throw new ParseException("Invalid destination branch name: " + branchName, -1);
    }

    GitRemote remote = findRemote(repository.getRemotes(), remoteName);
    if (remote == null) {
      LOG.error("Remote [" + remoteName + "] is not found among " + repository.getRemotes());
      throw new ParseException("Invalid remote: " + remoteName, -1);
    }

    GitRemoteBranch existingRemoteBranch = findRemoteBranch(repository, remote, branchName);
    if (existingRemoteBranch != null) {
      return new GitPushTarget(existingRemoteBranch, false);
    }
    GitRemoteBranch rb = new GitStandardRemoteBranch(remote, branchName, GitBranch.DUMMY_HASH);
    return new GitPushTarget(rb, true);
  }

  @Nullable
  private static GitRemote findRemote(@NotNull Collection<GitRemote> remotes, @NotNull final String candidate) {
    return ContainerUtil.find(remotes, new Condition<GitRemote>() {
      @Override
      public boolean value(GitRemote remote) {
        return remote.getName().equals(candidate);
      }
    });
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GitPushTarget)) return false;

    GitPushTarget target = (GitPushTarget)o;

    if (myIsNewBranchCreated != target.myIsNewBranchCreated) return false;
    if (!myRemoteBranch.equals(target.myRemoteBranch)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myRemoteBranch.hashCode();
    result = 31 * result + (myIsNewBranchCreated ? 1 : 0);
    return result;
  }
}
