// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.reset;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.Hash;
import git4idea.GitActivity;
import git4idea.branch.GitBranchUiHandlerImpl;
import git4idea.branch.GitSmartOperationDialog;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.util.GitPreservingProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;

import static git4idea.GitNotificationIdsHolder.*;
import static git4idea.GitUtil.*;
import static git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector.Operation.RESET;

public class GitResetOperation {

  private final @NotNull Project myProject;
  private final @NotNull Map<GitRepository, @NotNull String> myCommits;
  private final @NotNull GitResetMode myMode;
  private final @NotNull ProgressIndicator myIndicator;
  private final @NotNull Git myGit;
  private final @NotNull VcsNotifier myNotifier;
  private final @NotNull GitBranchUiHandlerImpl myUiHandler;
  private final @NotNull OperationPresentation myPresentation;

  public GitResetOperation(@NotNull Project project,
                           @NotNull Map<GitRepository, Hash> targetCommits,
                           @NotNull GitResetMode mode,
                           @NotNull ProgressIndicator indicator) {
    this(project, ContainerUtil.map2Map(targetCommits.entrySet(), e -> Pair.create(e.getKey(), e.getValue().asString())),
         mode, indicator, new OperationPresentation());
  }

  public GitResetOperation(@NotNull Project project,
                           @NotNull Map<GitRepository, @NotNull String> targetCommits,
                           @NotNull GitResetMode mode,
                           @NotNull ProgressIndicator indicator,
                           @NotNull OperationPresentation operationPresentation) {
    myProject = project;
    myCommits = targetCommits;
    myMode = mode;
    myIndicator = indicator;
    myPresentation = operationPresentation;
    myGit = Git.getInstance();
    myNotifier = VcsNotifier.getInstance(project);
    myUiHandler = new GitBranchUiHandlerImpl(myProject, indicator);
  }

  public boolean execute() {
    saveAllDocuments();
    Map<GitRepository, GitCommandResult> results = new HashMap<>();
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, GitBundle.message(myPresentation.activityName), GitActivity.Reset)) {
      for (Map.Entry<GitRepository, String> entry : myCommits.entrySet()) {
        GitRepository repository = entry.getKey();
        VirtualFile root = repository.getRoot();
        String target = entry.getValue();
        GitLocalChangesWouldBeOverwrittenDetector detector = new GitLocalChangesWouldBeOverwrittenDetector(root, RESET);

        Hash startHash = getHead(repository);

        GitCommandResult result = myGit.reset(repository, myMode, target, detector);
        if (!result.success() && detector.isDetected()) {
          GitCommandResult smartResult = proposeSmartReset(detector, repository, target);
          if (smartResult != null) {
            result = smartResult;
          }
        }
        results.put(repository, result);

        updateAndRefreshChangedVfs(repository, startHash);
        VcsDirtyScopeManager.getInstance(myProject).dirDirtyRecursively(root);
        repository.getUntrackedFilesHolder().invalidate(); // 'git reset --mixed' may make a file untracked without changing anything else
      }
    }
    notifyResult(results);

    return ContainerUtil.all(results.values(), GitCommandResult::success);
  }

  private GitCommandResult proposeSmartReset(@NotNull GitLocalChangesWouldBeOverwrittenDetector detector,
                                             final @NotNull GitRepository repository, @NotNull @NlsSafe String target) {
    Collection<String> absolutePaths = toAbsolute(repository.getRoot(), detector.getRelativeFilePaths());
    List<Change> affectedChanges = findLocalChangesForPaths(myProject, repository.getRoot(), absolutePaths, false);
    GitSmartOperationDialog.Choice choice = myUiHandler.showSmartOperationDialog(myProject, affectedChanges, absolutePaths,
                                                                                 GitBundle.message(myPresentation.operationTitle),
                                                                                 GitBundle.message(myPresentation.forceButtonTitle));
    if (choice == GitSmartOperationDialog.Choice.SMART) {
      final Ref<GitCommandResult> result = Ref.create();
      GitSaveChangesPolicy saveMethod = GitVcsSettings.getInstance(myProject).getSaveChangesPolicy();
      new GitPreservingProcess(myProject, myGit, Collections.singleton(repository.getRoot()),
                               GitBundle.message(myPresentation.operationTitle),
                               target, saveMethod, myIndicator,
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
      myNotifier.notifySuccess(RESET_SUCCESSFUL, "", GitBundle.message(myPresentation.notificationSuccess));
    }
    else if (!successes.isEmpty()) {
      myNotifier.notifyImportantWarning(RESET_PARTIALLY_FAILED,
                                        GitBundle.message(myPresentation.notificationPartialFailureTitle),
                                        GitBundle.message(myPresentation.notificationPartialFailureMessage,
                                                          joinRepos(successes.keySet()),
                                                          joinRepos(errors.keySet()),
                                                          formErrorReport(errors)));
    }
    else {
      myNotifier.notifyError(RESET_FAILED, GitBundle.message(myPresentation.notificationFailure), formErrorReport(errors), true);
    }
  }

  private static @NlsSafe @NotNull String formErrorReport(@NotNull Map<GitRepository, GitCommandResult> errorResults) {
    MultiMap<String, GitRepository> grouped = groupByResult(errorResults);
    if (grouped.size() == 1) {
      return "<code>" + grouped.keySet().iterator().next() + "</code>";
    }
    return StringUtil.join(grouped.entrySet(), entry -> joinRepos(entry.getValue()) + ":<br/><code>" + entry.getKey() + "</code>", "<br/>");
  }

  // to avoid duplicate error reports if they are the same for different repositories
  private static @NotNull MultiMap<String, GitRepository> groupByResult(@NotNull Map<GitRepository, GitCommandResult> results) {
    MultiMap<String, GitRepository> grouped = MultiMap.create();
    for (Map.Entry<GitRepository, GitCommandResult> entry : results.entrySet()) {
      grouped.putValue(entry.getValue().getErrorOutputAsHtmlString(), entry.getKey());
    }
    return grouped;
  }

  private static @NlsSafe @NotNull String joinRepos(@NotNull Collection<? extends GitRepository> repositories) {
    return StringUtil.join(DvcsUtil.sortRepositories(repositories), ", ");
  }

  private static void saveAllDocuments() {
    ApplicationManager.getApplication().invokeAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());
  }

  public static @PropertyKey(resourceBundle = GitBundle.BUNDLE) class OperationPresentation {
    public String activityName = "activity.name.reset";
    public String operationTitle = "git.reset.operation";
    public String forceButtonTitle = "git.reset.hard.button";
    public String notificationSuccess = "git.reset.successful.notification.message";
    public String notificationFailure = "git.reset.failed.notification.title";
    public String notificationPartialFailureTitle = "git.reset.partially.failed.notification.title";
    /**
     * {0} success repos, {1} failure repos, {2} error message
     */
    public String notificationPartialFailureMessage = "git.reset.partially.failed.notification.msg";
  }
}
