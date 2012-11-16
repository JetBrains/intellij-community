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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsException;
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
 * <p>The contract of the current implementations (probably, some or the limitations will be removed in the future):
 * <ul>
 *   <li>If you want to request outgoing commits (and force refresh, even if they were previously collected),
 *       call {@link #collect(ResultHandler)}. {@link GitPushDialog} should do it to get the up-to-date list of commits to push.</li>
 *   <li>If you want to get the list of outgoing commits, but not sure if the list is ready, call
 *       {@link #waitForCompletionAndGetCommits(boolean)} and {@link #getCommits() get commits}.
 *       It will collect commits if they were not collected yet. It won't refresh the list if
 *       commits were collected, unless you specify the {@code refresh} parameter to it. <br/><br/>
 *       The latter is needed, when pushing without opening a push dialog (Commit & Push and if this behavior is switched on in the
 *       settings): otherwise we will get the list of commits requested by previous push dialog, which might be very out-of-date.</li>
 * </ul>
 * </p>
 *
 * @author Kirill Likhodedov
 */
class GitOutgoingCommitsCollector {

  private static final Logger LOG = GitLogger.PUSH_LOG;

  @NotNull private final Project myProject;
  @NotNull private final GitPlatformFacade myPlatformFacade;

  @NotNull private State myState;
  @NotNull private final Object STATE_LOCK = new Object();
  @NotNull private final Queue<ResultHandler> completionHandlers = new ArrayDeque<ResultHandler>();
  private int refreshWaiters;

  @NotNull private GitCommitsByRepoAndBranch myCommits = GitCommitsByRepoAndBranch.empty();
  @Nullable private String myError;

  /**
   * Individual locks for threads which request {@link #waitForCompletionAndGetCommits(boolean)}.
   */
  @NotNull private final ThreadLocal<Object> WAITER_LOCK = new ThreadLocal<Object>();

  /**
   * Pass an instance of this handler to {@link #collect(ResultHandler)} to handle result when collecting of outgoing commits completes.
   */
  interface ResultHandler {
    void onSuccess(GitCommitsByRepoAndBranch commits);
    void onError(String error);
  }

  private static final ResultHandler EMPTY_RESULT_HANDLER = new ResultHandler() {
    @Override
    public void onSuccess(GitCommitsByRepoAndBranch commits) {
    }

    @Override
    public void onError(String error) {
    }
  };


  /**
   * Current state of the component.
   */
  private enum State {
    EMPTY, // nothing was collected, need to load.
    BUSY,  // currently loading, please wait.
    READY  // everything loaded; refresh may be requested via the collect() method
  }

  static GitOutgoingCommitsCollector getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitOutgoingCommitsCollector.class);
  }

  GitOutgoingCommitsCollector(@NotNull Project project, @NotNull GitPlatformFacade facade) {
    myProject = project;
    myPlatformFacade = facade;
    myState = State.EMPTY;
  }

  /**
   * Collect commits (if not yet collected) in background and invoke the given runnable after completion.
   * @param onComplete Executed after completed (successful or failed) execution of the task.
   *                   It is executed in the current thread, so if you need it on AWT, include "invokeLater" to the handler.
   */
  void collect(@Nullable ResultHandler onComplete) {
    synchronized (STATE_LOCK) {
      if (myState == State.READY || myState == State.EMPTY) { // start initial collecting or refresh already collected info
        myState = State.BUSY;
        ApplicationManager.getApplication().executeOnPooledThread(new Updater());
      }
      else if (myState == State.BUSY) { // somebody already started collection => we request more up-to-date info afterwards.
        refreshWaiters++;
      }

      // register the action that will be run after collection completes.
      // important to add at least fake action not to break the order of [collection requested-execute post-action].
      completionHandlers.offer(onComplete != null ? onComplete : EMPTY_RESULT_HANDLER);
    }
  }

  /**
   * <ul>
   *   <li>If collection has completed, and no need to {@code refresh}, immediately returns.</li>
   *   <li>If collection is in progress, waits for the completion.
   *       But if {@code refresh} is needed, starts new completion to get up-to-date results.</li>
   *   <li>If collection hasn't been started yet, starts it and waits for the completion.</li>
   * </ul>
   * @param refresh If the list of commits need to be re-queries even if we already have a version of it.
   */
  GitCommitsByRepoAndBranch waitForCompletionAndGetCommits(boolean refresh) {
    // if nobody has initialized the collection yet, or we need up-to-date version suspecting that something might have changed,
    // then initialize collection.
    synchronized (STATE_LOCK) {
      if (myState == State.EMPTY || refresh) {
        collect(null); // makes State BUSY and starts collection
      }
    }

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
      return myState == State.READY;
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

  private boolean errorHappened() {
    return getError() != null;
  }

  private void doCollect() {
    // currently we clear the collected information before each collect.
    // TODO Later we will persist it (providing in the Outgoing view) and update on push and other operations
    synchronized (STATE_LOCK) {
      myCommits.clear();
      myError = null;
    }

    try {
      GitCommitsByRepoAndBranch commits = collectOutgoingCommits(GitPushUtil.getSpecsToPushForAllRepositories(myPlatformFacade, myProject));
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
    if (errorHappened()) {
      handler.onError(getError());
    }
    else {
      handler.onSuccess(getCommits());
    }
  }

  // executed on a pooled thread
  private class Updater implements Runnable {
    public void run() {
      doCollect();
      synchronized (STATE_LOCK) {
        if (refreshWaiters > 0) { // if collection was requested again, we need to refresh information
          refreshWaiters = 0;     // but only once: we will get the up-to-date information anyway.

          // execute the correspondent handler
          handleResult(completionHandlers.poll());

          // queue the next update (in a separate thread to release the lock and return).
          ApplicationManager.getApplication().executeOnPooledThread(new Updater());
        }
        else {
          myState = State.READY;
          // when we are completely up-to-date execute all remaining tasks
          while (!completionHandlers.isEmpty()) {
            handleResult(completionHandlers.poll());
          }
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
  private static Map<GitRepository, List<GitBranchPair>> prepareReposAndBranchesToPush(@NotNull Map<GitRepository, GitPushSpec> pushSpecs)
    throws VcsException
  {
    Set<GitRepository> repositories = pushSpecs.keySet();
    Map<GitRepository, List<GitBranchPair>> res = new HashMap<GitRepository, List<GitBranchPair>>();
    for (GitRepository repository : repositories) {
      GitPushSpec pushSpec = pushSpecs.get(repository);
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
      GitLocalBranch source = sourceDest.getBranch();
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
