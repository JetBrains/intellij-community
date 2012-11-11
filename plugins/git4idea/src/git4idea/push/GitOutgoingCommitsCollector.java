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
package git4idea.push;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.QueueProcessor;
import git4idea.*;
import git4idea.branch.GitBranchPair;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Collects outgoing commits (commits to be pushed) for all repositories and holds this information.
 *
 * <p>The contract of the current implementation (probably, some or the limitations will be removed in the future):
 * <ul>
 *   <li>If you want to request outgoing commits (and force refresh, even if they were previously collected),
 *       call {@link #collect(GitPushSpecs, ResultHandler)}. {@link GitPushDialog} should do it to get the up-to-date list of commits to push.</li>
 *   <li>If you want just to get the list of outgoing commits, but not sure if the list is ready, call
 *       {@link #waitForCompletionAndGetCommits()} and {@link #getCommits() get commits}.
 *       It will collect commits if they were not collected yet.</li>
 * </ul>
 * </p>
 *
 * @author Kirill Likhodedov
 */
class GitOutgoingCommitsCollector {

  private static final Logger LOG = GitLogger.PUSH_LOG;

  @NotNull private final QueueProcessor<GitPushSpecs> myProcessor = new QueueProcessor<GitPushSpecs>(new Collector());
  @NotNull private final Object STATE_LOCK = new Object();
  @NotNull private final Queue<ResultHandler> myResultHandlers = new ArrayDeque<ResultHandler>();

  @NotNull private GitCommitsByRepoAndBranch myCommits = GitCommitsByRepoAndBranch.empty();
  @Nullable private String myError;

  /**
   * Individual locks for threads which request {@link #waitForCompletionAndGetCommits()}.
   */
  @NotNull private final ThreadLocal<Object> WAITER_LOCK = new ThreadLocal<Object>();

  /**
   * Pass an instance of this handler to {@link #collect(GitPushSpecs, ResultHandler)}
   * to handle result when collecting of outgoing commits completes.
   */
  interface ResultHandler {
    void onSuccess(@NotNull GitCommitsByRepoAndBranch commits);
    void onError(@NotNull String error);
  }

  private static final ResultHandler EMPTY_RESULT_HANDLER = new ResultHandler() {
    @Override
    public void onSuccess(@NotNull GitCommitsByRepoAndBranch commits) {
    }

    @Override
    public void onError(@NotNull String error) {
    }
  };


  static GitOutgoingCommitsCollector getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitOutgoingCommitsCollector.class);
  }

  /**
   * Collect commits (if not yet collected) in background and invoke the given runnable after completion.
   * @param onComplete Executed after completed (successful or failed) execution of the task.
   *                   It is executed in the current thread, so if you need it on AWT, include "invokeLater" to the handler.
   */
  void collect(@NotNull GitPushSpecs pushSpecs, @Nullable ResultHandler onComplete) {
    synchronized (STATE_LOCK) {
      // if several collect requests go one by one, it is enough to have only one update: it will receive the up-to-date information.
      // so we are removing any other pending requests.
      myProcessor.clear();
      myProcessor.add(pushSpecs);

      // register the action that will be run after collection completes.
      // important to add at least a fake action not to break the order of [collection requested-execute post-action].
      myResultHandlers.offer(onComplete != null ? onComplete : EMPTY_RESULT_HANDLER);
    }
  }

  /**
   * <ul>
   *   <li>If collection has completed immediately returns.</li>
   *   <li>If collection is in progress, waits for the completion.</li>
   * </ul>
   */
  @NotNull
  GitCommitsByRepoAndBranch waitForCompletionAndGetCommits() {
    while (!isReady()) {
      try {
        synchronized (WAITER_LOCK) {
          TimeUnit.MILLISECONDS.timedWait(WAITER_LOCK, 100);
        }
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
    }

    return getCommits();
  }

  private boolean isReady() {
    synchronized (STATE_LOCK) {
      return myProcessor.isEmpty();
    }
  }

  @NotNull
  private GitCommitsByRepoAndBranch getCommits() {
    synchronized (STATE_LOCK) {
      return new GitCommitsByRepoAndBranch(myCommits);
    }
  }

  @Nullable
  private String getError() {
    synchronized (STATE_LOCK) {
      return myError;
    }
  }

  private void doCollect(@NotNull GitPushSpecs pushSpecs) {
    // currently we clear the collected information before each collect.
    // TODO Later we will persist it (providing in the Outgoing view) and update on push and other operations
    synchronized (STATE_LOCK) {
      myCommits.clear();
      myError = null;
    }

    try {
      GitCommitsByRepoAndBranch commits = collectOutgoingCommits(pushSpecs);
      synchronized (STATE_LOCK) {
        myCommits = commits;
        myError = null;
      }
    }
    catch (VcsException e) {
      LOG.info("Collecting outgoing commits failed: " + e.getMessage(), e);
      synchronized (STATE_LOCK) {
        myError = e.getMessage(); // todo maybe should have more descriptive message here, need to check
      }
    }
  }

  private void handleResult(@Nullable ResultHandler handler) {
    if (handler == null) {
      return;
    }
    String error = getError();
    if (error != null) {
      handler.onError(error);
    }
    else {
      handler.onSuccess(getCommits());
    }
  }

  // executed on a pooled thread
  private class Collector implements Consumer<GitPushSpecs> {

    @Override
    public void consume(@NotNull GitPushSpecs pushSpecs) {
      doCollect(pushSpecs);
      synchronized (STATE_LOCK) {
        if (!myProcessor.hasPendingJobs()) {
          // when we are completely up-to-date, execute all remaining tasks
          while (!myResultHandlers.isEmpty()) {
            handleResult(myResultHandlers.poll());
          }
        }
        else {
          // execute only the correspondent handler
          handleResult(myResultHandlers.poll());
        }
      }
    }

  }

  /*******************************************  ACTUAL COMMITS COLLECTION IS BELOW ********************************************************/

  @NotNull
  private static GitCommitsByRepoAndBranch collectOutgoingCommits(@NotNull GitPushSpecs pushSpecs) throws VcsException {
    // TODO remove when tested
    if (Registry.is("git.pause.when.collecting.outgoing.commits")) {
      try {
        TimeUnit.SECONDS.sleep(5);
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
    }

    Map<GitRepository, List<GitBranchPair>> reposAndBranchesToPush = prepareReposAndBranchesToPush(pushSpecs.getSpecs());
    Set<GitRepository> repositories = reposAndBranchesToPush.keySet();

    Map<GitRepository, GitCommitsByBranch> commitsByRepoAndBranch = new HashMap<GitRepository, GitCommitsByBranch>();
    for (GitRepository repository : repositories) {
      List<GitBranchPair> branchPairs = reposAndBranchesToPush.get(repository);
      if (branchPairs == null) {
        continue;
      }
      GitCommitsByBranch commitsByBranch = collectsCommitsToPush(repository, branchPairs);
      commitsByRepoAndBranch.put(repository, commitsByBranch);
    }
    return new GitCommitsByRepoAndBranch(commitsByRepoAndBranch);
  }

  @NotNull
  private static Map<GitRepository, List<GitBranchPair>> prepareReposAndBranchesToPush(@NotNull Map<GitRepository, GitBranchPair> pushSpecs)
    throws VcsException
  {
    Set<GitRepository> repositories = pushSpecs.keySet();
    Map<GitRepository, List<GitBranchPair>> res = new HashMap<GitRepository, List<GitBranchPair>>();
    for (GitRepository repository : repositories) {
      GitBranchPair pushSpec = pushSpecs.get(repository);
      if (pushSpec == null) {
        continue;
      }
      res.put(repository, Collections.singletonList(new GitBranchPair(pushSpec.getSource(), pushSpec.getDest())));
    }
    return res;
  }

  @NotNull
  private static GitCommitsByBranch collectsCommitsToPush(@NotNull GitRepository repository,
                                                          @NotNull List<GitBranchPair> sourcesDestinations) throws VcsException {
    Map<GitBranch, GitPushBranchInfo> commitsByBranch = new HashMap<GitBranch, GitPushBranchInfo>();

    for (GitBranchPair sourceDest : sourcesDestinations) {
      GitLocalBranch source = sourceDest.getSource();
      GitRemoteBranch dest = sourceDest.getDest();

      List<GitCommit> commits = Collections.emptyList();
      GitPushBranchInfo.Type type;
      if (dest == null) {
        dest = GitPusher.NO_TARGET_BRANCH;
        type = GitPushBranchInfo.Type.NO_TRACKED_OR_TARGET;
      }
      else if (GitUtil.repoContainsRemoteBranch(repository, dest)) {
        commits = collectCommitsToPush(repository, source.getName(), dest.getName());
        type = GitPushBranchInfo.Type.STANDARD;
      }
      else {
        type = GitPushBranchInfo.Type.NEW_BRANCH;
      }
      commitsByBranch.put(source, new GitPushBranchInfo(source, dest, commits, type));
    }

    return new GitCommitsByBranch(commitsByBranch);
  }

  @NotNull
  private static List<GitCommit> collectCommitsToPush(@NotNull GitRepository repository,
                                                      @NotNull String source, @NotNull String destination) throws VcsException {
    return GitHistoryUtils.history(repository.getProject(), repository.getRoot(), destination + ".." + source);
  }

}
