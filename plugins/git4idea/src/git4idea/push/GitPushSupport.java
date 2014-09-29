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

import com.intellij.dvcs.push.*;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import git4idea.config.GitSharedSettings;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersionSpecialty;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class GitPushSupport extends PushSupport<GitRepository, GitPushSource, GitPushTarget> {

  private static final Logger LOG = Logger.getInstance(GitPushSupport.class);

  @NotNull private final GitRepositoryManager myRepositoryManager;
  @NotNull private final GitVcs myVcs;
  @NotNull private final Pusher<GitRepository, GitPushSource, GitPushTarget> myPusher;
  @NotNull private final OutgoingCommitsProvider<GitRepository, GitPushSource, GitPushTarget> myOutgoingCommitsProvider;
  @NotNull private final GitVcsSettings mySettings;
  private final GitSharedSettings mySharedSettings;

  // instantiated from plugin.xml
  @SuppressWarnings("UnusedDeclaration")
  private GitPushSupport(@NotNull Project project, @NotNull GitRepositoryManager repositoryManager) {
    myRepositoryManager = repositoryManager;
    myVcs = ObjectUtils.assertNotNull(GitVcs.getInstance(project));
    mySettings = GitVcsSettings.getInstance(project);
    myPusher = new GitPusher(project, mySettings);
    myOutgoingCommitsProvider = new GitOutgoingCommitsProvider(project);
    mySharedSettings = ServiceManager.getService(project, GitSharedSettings.class);
  }

  @NotNull
  @Override
  public AbstractVcs getVcs() {
    return myVcs;
  }

  @NotNull
  @Override
  public Pusher<GitRepository, GitPushSource, GitPushTarget> getPusher() {
    return myPusher;
  }

  @NotNull
  @Override
  public OutgoingCommitsProvider<GitRepository, GitPushSource, GitPushTarget> getOutgoingCommitsProvider() {
    return myOutgoingCommitsProvider;
  }

  @Nullable
  @Override
  public GitPushTarget getDefaultTarget(@NotNull GitRepository repository) {
    GitLocalBranch currentBranch = repository.getCurrentBranch();
    if (currentBranch == null) {
      return null;
    }
    GitBranchTrackInfo trackInfo = GitBranchUtil.getTrackInfoForBranch(repository, currentBranch);
    if (trackInfo != null) {
      return new GitPushTarget(trackInfo.getRemoteBranch(), false);
    }
    return proposeTargetForNewBranch(repository, currentBranch);
  }

  private static GitPushTarget proposeTargetForNewBranch(GitRepository repository, GitLocalBranch currentBranch) {
    Collection<GitRemote> remotes = repository.getRemotes();
    if (remotes.isEmpty()) {
      return null; // TODO need to propose to declare new remote
    }
    else if (remotes.size() == 1) {
      return makeTargetForNewBranch(repository, remotes.iterator().next(), currentBranch);
    }
    else {
      GitRemote remote = GitUtil.getDefaultRemote(remotes);
      if (remote == null) {
        remote = remotes.iterator().next();
      }
      return makeTargetForNewBranch(repository, remote, currentBranch);
    }
  }

  @NotNull
  private static GitPushTarget makeTargetForNewBranch(@NotNull GitRepository repository,
                                                      @NotNull GitRemote remote,
                                                      @NotNull GitLocalBranch currentBranch) {
    GitRemoteBranch existingRemoteBranch = GitPushTarget.findRemoteBranch(repository, remote, currentBranch.getName());
    if (existingRemoteBranch != null) {
      return new GitPushTarget(existingRemoteBranch, false);
    }
    return new GitPushTarget(new GitStandardRemoteBranch(remote, currentBranch.getName(), GitBranch.DUMMY_HASH), true);
  }

  @NotNull
  @Override
  public GitPushSource getSource(@NotNull GitRepository repository) {
    return new GitPushSource(repository.getCurrentBranch()); // TODO assert: detached head => not possible to push
  }

  @NotNull
  @Override
  public RepositoryManager<GitRepository> getRepositoryManager() {
    return myRepositoryManager;
  }

  @NotNull
  @Override
  public PushTargetPanel<GitPushTarget> createTargetPanel(@NotNull GitRepository repository, @Nullable GitPushTarget defaultTarget) {
    return new GitPushTargetPanel(repository, defaultTarget);
  }

  @Override
  public boolean isForcePushAllowed(@NotNull GitRepository repo, @NotNull GitPushTarget target) {
    final String targetBranch = target.getBranch().getNameForRemoteOperations();
    return !ContainerUtil.exists(mySharedSettings.getForcePushProhibitedPatterns(), new Condition<String>() {
      @Override
      public boolean value(String pattern) {
        return targetBranch.matches("^" + pattern + "$"); // let "master" match only "master" and not "any-master-here" by default
      }
    });
  }

  @Override
  public boolean isForcePushEnabled() {
    return mySettings.isForcePushAllowed();
  }

  @Nullable
  @Override
  public VcsPushOptionsPanel createOptionsPanel() {
    return new GitPushTagPanel(mySettings.getPushTagMode(), GitVersionSpecialty.SUPPORTS_FOLLOW_TAGS.existsIn(myVcs.getVersion()));
  }
}
