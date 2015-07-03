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
package git4idea.branch;

import com.intellij.openapi.Disposable;
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
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.ui.MergeCommitsHighlighter;
import git4idea.GitBranch;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitLineHandlerAdapter;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DeepComparator implements Disposable {

  private static final Logger LOG = Logger.getInstance(DeepComparator.class);

  @NotNull private final Project myProject;
  @NotNull private final GitRepositoryManager myRepositoryManager;
  @NotNull private final VcsLogUi myUi;
  @NotNull private final VcsLogListener myLogListener;

  @Nullable private VcsLogHighlighter myHighlighter;
  @Nullable private MyTask myTask;

  @NotNull
  public static DeepComparator getInstance(@NotNull Project project, @NotNull VcsLogUi ui) {
    DeepComparatorHolder holder = ServiceManager.getService(project, DeepComparatorHolder.class);
    return holder.getInstance(ui);
  }

  DeepComparator(@NotNull Project project, @NotNull GitRepositoryManager manager, @NotNull VcsLogUi ui, @NotNull Disposable parent) {
    myProject = project;
    myRepositoryManager = manager;
    myUi = ui;
    Disposer.register(parent, this);

    myLogListener = new VcsLogListener() {
      @Override
      public void onChange(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
        if (myTask == null) { // no task in progress => not interested in refresh events
          return;
        }

        if (refreshHappened) {
          // collect data
          String comparedBranch = myTask.myComparedBranch;
          Map<GitRepository, GitBranch> repositoriesWithCurrentBranches = myTask.myRepositoriesWithCurrentBranches;
          VcsLogDataProvider provider = myTask.myProvider;

          stopTask();

          // highlight again
          Map<GitRepository, GitBranch> repositories = getRepositories(myUi.getDataPack().getLogProviders(), comparedBranch);
          if (repositories.equals(repositoriesWithCurrentBranches)) { // but not if current branch changed
            highlightInBackground(comparedBranch, provider);
          }
        }
        else {
          VcsLogBranchFilter branchFilter = myUi.getFilterUi().getFilters().getBranchFilter();
          if (branchFilter == null ||
              branchFilter.getBranchNames().size() != 1 ||
              !branchFilter.getBranchNames().iterator().next().equals(myTask.myComparedBranch)) {
            stopAndUnhighlight();
          }
        }
      }
    };
    myUi.addLogListener(myLogListener);
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

    myTask = new MyTask(myProject, myUi, repositories, dataProvider, branchToCompare);
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
    if (myHighlighter != null) {
      myUi.removeHighlighter(myHighlighter);
    }
  }

  @Override
  public void dispose() {
    stopAndUnhighlight();
    myUi.removeLogListener(myLogListener);
  }

  public boolean hasHighlightingOrInProgress() {
    return myTask != null;
  }

  private class MyTask extends Task.Backgroundable {

    @NotNull private final Project myProject;
    @NotNull private final VcsLogUi myUi;
    @NotNull private final Map<GitRepository, GitBranch> myRepositoriesWithCurrentBranches;
    @NotNull private final VcsLogDataProvider myProvider;
    @NotNull private final String myComparedBranch;

    @NotNull private final TIntHashSet myNonPickedCommits = new TIntHashSet();
    @Nullable private VcsException myException;
    private boolean myCancelled;

    public MyTask(@NotNull Project project,
                  @NotNull VcsLogUi ui,
                  @NotNull Map<GitRepository, GitBranch> repositoriesWithCurrentBranches,
                  @NotNull VcsLogDataProvider dataProvider,
                  @NotNull String branchToCompare) {
      super(project, "Comparing branches...");
      myProject = project;
      myUi = ui;
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
          myNonPickedCommits
            .addAll(getNonPickedCommitsFromGit(myProject, repo.getRoot(), myProvider, currentBranch.getName(), myComparedBranch).toArray());
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

      myHighlighter = new VcsLogHighlighter() {
        @NotNull
        @Override
        public VcsCommitStyle getStyle(int commitIndex, boolean isSelected) {
          return VcsCommitStyleFactory.foreground(!myNonPickedCommits.contains(commitIndex) ? MergeCommitsHighlighter.MERGE_COMMIT_FOREGROUND : null);
        }
      };
      myUi.addHighlighter(myHighlighter);
    }

    public void cancel() {
      myCancelled = true;
    }

    @NotNull
    private TIntHashSet getNonPickedCommitsFromGit(@NotNull Project project,
                                                   @NotNull VirtualFile root,
                                                   @NotNull final VcsLogDataProvider dataProvider,
                                                   @NotNull String currentBranch,
                                                   @NotNull String comparedBranch) throws VcsException {
      GitLineHandler handler = new GitLineHandler(project, root, GitCommand.CHERRY);
      handler.addParameters(currentBranch, comparedBranch); // upstream - current branch; head - compared branch

      final TIntHashSet pickedCommits = new TIntHashSet();
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
              pickedCommits.add(dataProvider.getCommitIndex(hash));
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
