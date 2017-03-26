/*
` * Copyright 2000-2015 JetBrains s.r.o.
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
package git4idea.branch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.highlighters.MergeCommitsHighlighter;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import git4idea.GitBranch;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitLineHandlerAdapter;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class DeepComparator implements VcsLogHighlighter, Disposable {
  private static final Logger LOG = Logger.getInstance(DeepComparator.class);

  @NotNull private final Project myProject;
  @NotNull private final GitRepositoryManager myRepositoryManager;
  @NotNull private final VcsLogUi myUi;

  @Nullable private MyTask myTask;
  @Nullable private Set<CommitId> myNonPickedCommits;

  public DeepComparator(@NotNull Project project, @NotNull GitRepositoryManager manager, @NotNull VcsLogUi ui, @NotNull Disposable parent) {
    myProject = project;
    myRepositoryManager = manager;
    myUi = ui;
    Disposer.register(parent, this);
  }

  public void highlightInBackground(@NotNull String branchToCompare, @NotNull VcsLogDataProvider dataProvider) {
    if (myTask != null) {
      LOG.error("Shouldn't be possible");
      return;
    }

    Map<GitRepository, GitBranch> repositories = getRepositories(myUi.getDataPack().getLogProviders(), branchToCompare);
    if (repositories.isEmpty()) {
      removeHighlighting();
      return;
    }

    myTask = new MyTask(myProject, repositories, dataProvider, branchToCompare);
    myTask.queue();
  }

  @NotNull
  private Map<GitRepository, GitBranch> getRepositories(@NotNull Map<VirtualFile, VcsLogProvider> providers,
                                                        @NotNull String branchToCompare) {
    Map<GitRepository, GitBranch> repos = ContainerUtil.newHashMap();
    for (VirtualFile root : providers.keySet()) {
      GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
      if (repository == null || repository.getCurrentBranch() == null ||
          repository.getBranches().findBranchByName(branchToCompare) == null) {
        continue;
      }
      repos.put(repository, repository.getCurrentBranch());
    }
    return repos;
  }

  public void stopAndUnhighlight() {
    stopTask();
    removeHighlighting();
  }

  private void stopTask() {
    if (myTask != null) {
      myTask.cancel();
      myTask = null;
    }
  }

  private void removeHighlighting() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myNonPickedCommits = null;
  }

  @Override
  public void dispose() {
    stopAndUnhighlight();
  }

  public boolean hasHighlightingOrInProgress() {
    return myTask != null;
  }

  public static DeepComparator getInstance(@NotNull Project project, @NotNull VcsLogUi logUi) {
    return ServiceManager.getService(project, DeepComparatorHolder.class).getInstance(logUi);
  }

  @NotNull
  @Override
  public VcsLogHighlighter.VcsCommitStyle getStyle(@NotNull VcsShortCommitDetails commitDetails, boolean isSelected) {
    if (myNonPickedCommits == null) return VcsCommitStyle.DEFAULT;
    return VcsCommitStyleFactory.foreground(!myNonPickedCommits.contains(new CommitId(commitDetails.getId(), commitDetails.getRoot()))
                                            ? MergeCommitsHighlighter.MERGE_COMMIT_FOREGROUND
                                            : null);
  }

  @Override
  public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
    if (myTask == null) { // no task in progress => not interested in refresh events
      return;
    }

    String comparedBranch = myTask.myComparedBranch;
    VcsLogBranchFilter branchFilter = dataPack.getFilters().getBranchFilter();
    if (branchFilter == null || !myTask.myComparedBranch.equals(VcsLogUtil.getSingleFilteredBranch(branchFilter, dataPack.getRefs()))) {
      stopAndUnhighlight();
      return;
    }

    if (refreshHappened) {
      Map<GitRepository, GitBranch> repositoriesWithCurrentBranches = myTask.myRepositoriesWithCurrentBranches;
      VcsLogDataProvider provider = myTask.myProvider;

      stopTask();

      // highlight again
      Map<GitRepository, GitBranch> repositories = getRepositories(dataPack.getLogProviders(), comparedBranch);
      if (repositories.equals(repositoriesWithCurrentBranches)) {
        // but not if current branch changed
        highlightInBackground(comparedBranch, provider);
      }
      else {
        removeHighlighting();
      }
    }
  }

  public static class Factory implements VcsLogHighlighterFactory {
    @NotNull private static final String ID = "CHERRY_PICKED_COMMITS";

    @NotNull
    @Override
    public VcsLogHighlighter createHighlighter(@NotNull VcsLogData logDataManager, @NotNull VcsLogUi logUi) {
      return getInstance(logDataManager.getProject(), logUi);
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @NotNull
    @Override
    public String getTitle() {
      return "Cherry Picked Commits";
    }

    @Override
    public boolean showMenuItem() {
      return false;
    }
  }

  private class MyTask extends Task.Backgroundable {

    @NotNull private final Project myProject;
    @NotNull private final Map<GitRepository, GitBranch> myRepositoriesWithCurrentBranches;
    @NotNull private final VcsLogDataProvider myProvider;
    @NotNull private final String myComparedBranch;

    @NotNull private final Set<CommitId> myCollectedNonPickedCommits = ContainerUtil.newHashSet();
    @Nullable private VcsException myException;
    private boolean myCancelled;

    public MyTask(@NotNull Project project,
                  @NotNull Map<GitRepository, GitBranch> repositoriesWithCurrentBranches,
                  @NotNull VcsLogDataProvider dataProvider,
                  @NotNull String branchToCompare) {
      super(project, "Comparing Branches...");
      myProject = project;
      myRepositoriesWithCurrentBranches = repositoriesWithCurrentBranches;
      myProvider = dataProvider;
      myComparedBranch = branchToCompare;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        for (Map.Entry<GitRepository, GitBranch> entry : myRepositoriesWithCurrentBranches.entrySet()) {
          GitRepository repo = entry.getKey();
          GitBranch currentBranch = entry.getValue();
          myCollectedNonPickedCommits
            .addAll(getNonPickedCommitsFromGit(myProject, repo.getRoot(), currentBranch.getName(), myComparedBranch));
        }
      }
      catch (VcsException e) {
        LOG.warn(e);
        myException = e;
      }
    }

    @Override
    public void onSuccess() {
      if (myCancelled) {
        return;
      }

      removeHighlighting();

      if (myException != null) {
        VcsNotifier.getInstance(myProject).notifyError("Couldn't compare with branch " + myComparedBranch, myException.getMessage());
        return;
      }
      myNonPickedCommits = myCollectedNonPickedCommits;
    }

    public void cancel() {
      myCancelled = true;
    }

    @NotNull
    private Set<CommitId> getNonPickedCommitsFromGit(@NotNull Project project,
                                                     @NotNull final VirtualFile root,
                                                     @NotNull String currentBranch,
                                                     @NotNull String comparedBranch) throws VcsException {
      GitLineHandler handler = new GitLineHandler(project, root, GitCommand.CHERRY);
      handler.addParameters(currentBranch, comparedBranch); // upstream - current branch; head - compared branch

      final Set<CommitId> pickedCommits = ContainerUtil.newHashSet();
      handler.addLineListener(new GitLineHandlerAdapter() {
        @Override
        public void onLineAvailable(String line, Key outputType) {
          // + 645caac042ff7fb1a5e3f7d348f00e9ceea5c317
          // - c3b9b90f6c26affd7e597ebf65db96de8f7e5860
          if (line.startsWith("+")) {
            try {
              line = line.substring(2).trim();
              int firstSpace = line.indexOf(' ');
              if (firstSpace > 0) {
                line = line.substring(0, firstSpace); // safety-check: take just the first word for sure
              }
              Hash hash = HashImpl.build(line);
              pickedCommits.add(new CommitId(hash, root));
            }
            catch (Exception e) {
              LOG.error("Couldn't parse line [" + line + "]");
            }
          }
        }
      });
      handler.runInCurrentThread(null);
      return pickedCommits;
    }
  }
}