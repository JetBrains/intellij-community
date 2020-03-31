// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.reset;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.Hash;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUiHandlerImpl;
import git4idea.branch.GitSmartOperationDialog;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.repo.GitRepository;
import git4idea.util.GitPreservingProcess;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static git4idea.GitUtil.*;
import static git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector.Operation.RESET;

public class GitResetOperation {


  @NotNull private final Project myProject;
  @NotNull private final Map<GitRepository, Hash> myCommits;
  @NotNull private final GitResetMode myMode;
  @NotNull private final ProgressIndicator myIndicator;
  @NotNull private final Git myGit;
  @NotNull private final VcsNotifier myNotifier;
  @NotNull private final GitBranchUiHandlerImpl myUiHandler;

  public GitResetOperation(@NotNull Project project,
                           @NotNull Map<GitRepository, Hash> targetCommits,
                           @NotNull GitResetMode mode,
                           @NotNull ProgressIndicator indicator) {
    myProject = project;
    myCommits = targetCommits;
    myMode = mode;
    myIndicator = indicator;
    myGit = Git.getInstance();
    myNotifier = VcsNotifier.getInstance(project);
    myUiHandler = new GitBranchUiHandlerImpl(myProject, myGit, indicator);
  }

  public void execute() {
    saveAllDocuments();
    Map<GitRepository, GitCommandResult> results = new HashMap<>();
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, "Git Reset")) {
      for (Map.Entry<GitRepository, Hash> entry : myCommits.entrySet()) {
        GitRepository repository = entry.getKey();
        VirtualFile root = repository.getRoot();
        String target = entry.getValue().asString();
        GitLocalChangesWouldBeOverwrittenDetector detector = new GitLocalChangesWouldBeOverwrittenDetector(root, RESET);

        Hash startHash = getHead(repository);

        GitCommandResult result = myGit.reset(repository, myMode, target, detector);
        if (!result.success() && detector.wasMessageDetected()) {
          GitCommandResult smartResult = proposeSmartReset(detector, repository, target);
          if (smartResult != null) {
            result = smartResult;
          }
        }
        results.put(repository, result);

        updateAndRefreshChangedVfs(repository, startHash);
        VcsDirtyScopeManager.getInstance(myProject).dirDirtyRecursively(root);
      }
    }
    notifyResult(results);
  }

  private GitCommandResult proposeSmartReset(@NotNull GitLocalChangesWouldBeOverwrittenDetector detector,
                                             @NotNull final GitRepository repository, @NotNull final String target) {
    Collection<String> absolutePaths = GitUtil.toAbsolute(repository.getRoot(), detector.getRelativeFilePaths());
    List<Change> affectedChanges = GitUtil.findLocalChangesForPaths(myProject, repository.getRoot(), absolutePaths, false);
    GitSmartOperationDialog.Choice choice = myUiHandler.showSmartOperationDialog(myProject, affectedChanges, absolutePaths,
                                                                                 "reset", "&Hard Reset");
    if (choice == GitSmartOperationDialog.Choice.SMART) {
      final Ref<GitCommandResult> result = Ref.create();
      GitSaveChangesPolicy saveMethod = GitVcsSettings.getInstance(myProject).getSaveChangesPolicy();
      new GitPreservingProcess(myProject, myGit, Collections.singleton(repository.getRoot()), "reset", target,
                               saveMethod, myIndicator,
                               () -> result.set(myGit.reset(repository, myMode, target))).execute();
      return result.get();
    }
    if (choice == GitSmartOperationDialog.Choice.FORCE) {
      return myGit.reset(repository, GitResetMode.HARD, target);
    }
    return null;
  }

  private void notifyResult(@NotNull Map<GitRepository, GitCommandResult> results) {
    Map<GitRepository, GitCommandResult> successes = new HashMap<>();
    Map<GitRepository, GitCommandResult> errors = new HashMap<>();
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
      myNotifier.notifyError("Reset Failed", formErrorReport(errors), true);
    }
  }

  @NotNull
  private static String formErrorReport(@NotNull Map<GitRepository, GitCommandResult> errorResults) {
    MultiMap<String, GitRepository> grouped = groupByResult(errorResults);
    if (grouped.size() == 1) {
      return "<code>" + grouped.keySet().iterator().next() + "</code>";
    }
    return StringUtil.join(grouped.entrySet(), entry -> joinRepos(entry.getValue()) + ":<br/><code>" + entry.getKey() + "</code>", "<br/>");
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
  private static String joinRepos(@NotNull Collection<? extends GitRepository> repositories) {
    return StringUtil.join(DvcsUtil.sortRepositories(repositories), ", ");
  }

  private static void saveAllDocuments() {
    ApplicationManager.getApplication().invokeAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());
  }

}
