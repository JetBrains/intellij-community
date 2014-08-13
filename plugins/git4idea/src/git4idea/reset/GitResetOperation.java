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
package git4idea.reset;

import com.intellij.dvcs.repo.RepositoryUtil;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUiHandlerImpl;
import git4idea.branch.GitSmartOperationDialog;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector;
import git4idea.repo.GitRepository;
import git4idea.util.GitPreservingProcess;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector.Operation.RESET;

public class GitResetOperation {

  @NotNull private final Project myProject;
  @NotNull private final Map<GitRepository, VcsFullCommitDetails> myCommits;
  @NotNull private final GitResetMode myMode;
  @NotNull private final ProgressIndicator myIndicator;
  @NotNull private final Git myGit;
  @NotNull private final VcsNotifier myNotifier;
  @NotNull private final GitPlatformFacade myFacade;
  @NotNull private final GitBranchUiHandlerImpl myUiHandler;

  public GitResetOperation(@NotNull Project project, @NotNull Map<GitRepository, VcsFullCommitDetails> targetCommits,
                           @NotNull GitResetMode mode, @NotNull ProgressIndicator indicator) {
    myProject = project;
    myCommits = targetCommits;
    myMode = mode;
    myIndicator = indicator;
    myGit = ServiceManager.getService(Git.class);
    myNotifier = VcsNotifier.getInstance(project);
    myFacade = ServiceManager.getService(GitPlatformFacade.class);
    myUiHandler = new GitBranchUiHandlerImpl(myProject, myFacade, myGit, indicator);
  }

  public void execute() {
    saveAllDocuments();
    GitUtil.workingTreeChangeStarted(myProject);
    Map<GitRepository, GitCommandResult> results = ContainerUtil.newHashMap();
    try {
      for (Map.Entry<GitRepository, VcsFullCommitDetails> entry : myCommits.entrySet()) {
        GitRepository repository = entry.getKey();
        VirtualFile root = repository.getRoot();
        String target = entry.getValue().getId().asString();
        GitLocalChangesWouldBeOverwrittenDetector detector = new GitLocalChangesWouldBeOverwrittenDetector(root, RESET);

        GitCommandResult result = myGit.reset(repository, myMode, target, detector);
        if (!result.success() && detector.wasMessageDetected()) {
          GitCommandResult smartResult = proposeSmartReset(detector, repository, target);
          if (smartResult != null) {
            result = smartResult;
          }
        }
        results.put(repository, result);
        repository.update();
        VfsUtil.markDirtyAndRefresh(true, true, false, root);
      }
    }
    finally {
      GitUtil.workingTreeChangeFinished(myProject);
    }
    notifyResult(results);
  }

  private GitCommandResult proposeSmartReset(@NotNull GitLocalChangesWouldBeOverwrittenDetector detector,
                                             @NotNull final GitRepository repository, @NotNull final String target) {
    Collection<String> absolutePaths = GitUtil.toAbsolute(repository.getRoot(), detector.getRelativeFilePaths());
    List<Change> affectedChanges = GitUtil.findLocalChangesForPaths(myProject, repository.getRoot(), absolutePaths, false);
    int choice = myUiHandler.showSmartOperationDialog(myProject, affectedChanges, absolutePaths, "reset", "&Hard Reset");
    if (choice == GitSmartOperationDialog.SMART_EXIT_CODE) {
      final Ref<GitCommandResult> result = Ref.create();
      new GitPreservingProcess(myProject, myFacade, myGit, Collections.singleton(repository), "reset", target, myIndicator, new Runnable() {
        @Override
        public void run() {
          result.set(myGit.reset(repository, myMode, target));
        }
      }).execute();
      return result.get();
    }
    if (choice == GitSmartOperationDialog.FORCE_EXIT_CODE) {
      return myGit.reset(repository, GitResetMode.HARD, target);
    }
    return null;
  }

  private void notifyResult(@NotNull Map<GitRepository, GitCommandResult> results) {
    Map<GitRepository, GitCommandResult> successes = ContainerUtil.newHashMap();
    Map<GitRepository, GitCommandResult> errors = ContainerUtil.newHashMap();
    for (Map.Entry<GitRepository, GitCommandResult> entry : results.entrySet()) {
      GitCommandResult result = entry.getValue();
      GitRepository repository = entry.getKey();
      if (result.success()) {
        successes.put(repository, result);
      }
      else {
        errors.put(repository, result);
      }
    }

    if (errors.isEmpty()) {
      myNotifier.notifySuccess("", "Reset successful");
    }
    else if (!successes.isEmpty()) {
      myNotifier.notifyImportantWarning("Reset partially failed",
                                        "Reset was successful for " + joinRepos(successes.keySet())
                                        + "<br/>but failed for " + joinRepos(errors.keySet()) + ": <br/>" + formErrorReport(errors));
    }
    else {
      myNotifier.notifyError("Reset Failed", formErrorReport(errors));
    }
  }

  @NotNull
  private static String formErrorReport(@NotNull Map<GitRepository, GitCommandResult> errorResults) {
    MultiMap<String, GitRepository> grouped = groupByResult(errorResults);
    if (grouped.size() == 1) {
      return "<code>" + grouped.keySet().iterator().next() + "</code>";
    }
    return StringUtil.join(grouped.entrySet(), new Function<Map.Entry<String, Collection<GitRepository>>, String>() {
      @NotNull
      @Override
      public String fun(@NotNull Map.Entry<String, Collection<GitRepository>> entry) {
        return joinRepos(entry.getValue()) + ":<br/><code>" + entry.getKey() + "</code>";
      }
    }, "<br/>");
  }

  // to avoid duplicate error reports if they are the same for different repositories
  @NotNull
  private static MultiMap<String, GitRepository> groupByResult(@NotNull Map<GitRepository, GitCommandResult> results) {
    MultiMap<String, GitRepository> grouped = MultiMap.create();
    for (Map.Entry<GitRepository, GitCommandResult> entry : results.entrySet()) {
      grouped.putValue(entry.getValue().getErrorOutputAsHtmlString(), entry.getKey());
    }
    return grouped;
  }

  @NotNull
  private static String joinRepos(@NotNull Collection<GitRepository> repositories) {
    return StringUtil.join(RepositoryUtil.sortRepositories(repositories), ", ");
  }

  private static void saveAllDocuments() {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

}
