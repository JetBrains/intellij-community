// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import git4idea.commands.Git;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.stash.GitChangesSaver;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.xml.util.XmlStringUtil.wrapInHtml;
import static com.intellij.xml.util.XmlStringUtil.wrapInHtmlTag;
import static git4idea.GitNotificationIdsHolder.COULD_NOT_SAVE_UNCOMMITTED_CHANGES;

/**
 * Executes a Git operation on a number of repositories surrounding it by stash-unstash procedure.
 * I.e. stashes changes, executes the operation and then unstashes it.
 */
public class GitPreservingProcess {

  private static final Logger LOG = Logger.getInstance(GitPreservingProcess.class);

  private final @NotNull Project myProject;
  private final @NotNull Git myGit;
  private final @NotNull Collection<? extends VirtualFile> myRootsToSave;
  private final @NotNull @Nls String myOperationTitle;
  private final @NotNull @Nls String myDestinationName;
  private final @NotNull ProgressIndicator myProgressIndicator;
  private final @NotNull Runnable myOperation;
  private final @NotNull @Nls String myStashMessage;
  private final @NotNull GitChangesSaver mySaver;

  private final @NotNull AtomicBoolean myLoaded = new AtomicBoolean();

  public GitPreservingProcess(@NotNull Project project,
                              @NotNull Git git,
                              @NotNull Collection<? extends VirtualFile> rootsToSave,
                              @Nls @NotNull String operationTitle,
                              @Nls @NotNull String destinationName,
                              @NotNull GitSaveChangesPolicy saveMethod,
                              @NotNull ProgressIndicator indicator,
                              @NotNull Runnable operation) {
    this(project, git, rootsToSave, operationTitle, destinationName, saveMethod, indicator, true, operation);
  }

  @ApiStatus.Internal
  public GitPreservingProcess(@NotNull Project project,
                              @NotNull Git git,
                              @NotNull Collection<? extends VirtualFile> rootsToSave,
                              @Nls @NotNull String operationTitle,
                              @Nls @NotNull String destinationName,
                              @NotNull GitSaveChangesPolicy saveMethod,
                              @NotNull ProgressIndicator indicator,
                              boolean reportLocalHistoryActivity,
                              @NotNull Runnable operation) {
    myProject = project;
    myGit = git;
    myRootsToSave = rootsToSave;
    myOperationTitle = operationTitle;
    myDestinationName = destinationName;
    myProgressIndicator = indicator;
    myOperation = operation;
    myStashMessage = VcsBundle.message(
      "stash.changes.message.with.date",
      StringUtil.capitalize(myOperationTitle),
      DateFormatUtil.formatDateTime(Clock.getTime())
    );
    mySaver = configureSaver(saveMethod, reportLocalHistoryActivity);
  }

  public void execute() {
    execute(null);
  }

  public void execute(final @Nullable Computable<Boolean> autoLoadDecision) {
    Runnable operation = () -> {
      boolean savedSuccessfully = ProgressManager.getInstance().computeInNonCancelableSection(() -> save());
      LOG.debug("save result: " + savedSuccessfully);
      if (savedSuccessfully) {
        try {
          LOG.debug("running operation");
          myOperation.run();
          LOG.debug("operation completed.");
        }
        finally {
          if (autoLoadDecision == null || autoLoadDecision.compute()) {
            LOG.debug("loading");
            ProgressManager.getInstance().executeNonCancelableSection(() -> load());
          }
          else {
            mySaver.notifyLocalChangesAreNotRestored(myOperationTitle);
          }
        }
      }
      LOG.debug("finished.");
    };

    new GitFreezingProcess(myProject, myOperationTitle, operation).execute();
  }

  /**
   * Configures the saver: i.e. notifications and texts for the GitConflictResolver used inside.
   */
  private @NotNull GitChangesSaver configureSaver(@NotNull GitSaveChangesPolicy saveMethod, boolean reportLocalHistoryActivity) {
    GitChangesSaver saver = GitChangesSaver.getSaver(myProject, myGit, myProgressIndicator, myStashMessage, saveMethod, reportLocalHistoryActivity);
    MergeDialogCustomizer mergeDialogCustomizer = new MergeDialogCustomizer() {
      @Override
      public @NotNull String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
        return wrapInHtml(
          GitBundle.message(
            "restore.conflict.dialog.description.label.text",
            myOperationTitle,
            wrapInHtmlTag(myDestinationName, "code")
          )
        );
      }

      @Override
      public @NotNull String getLeftPanelTitle(@NotNull VirtualFile file) {
        return saveMethod.selectBundleMessage(
          GitBundle.message("restore.conflict.diff.dialog.left.stash.title"),
          GitBundle.message("restore.conflict.diff.dialog.left.shelf.title")
        );
      }

      @Override
      public @NotNull String getRightPanelTitle(@NotNull VirtualFile file, VcsRevisionNumber revisionNumber) {
        return wrapInHtml(GitBundle.message("restore.conflict.diff.dialog.right.title", wrapInHtmlTag(myDestinationName, "b")));
      }
    };

    GitConflictResolver.Params params = new GitConflictResolver.Params(myProject).
      setReverse(true).
      setMergeDialogCustomizer(mergeDialogCustomizer).
      setErrorNotificationTitle(GitBundle.message("preserving.process.local.changes.not.restored.error.title"));

    saver.setConflictResolverParams(params);
    return saver;
  }

  /**
   * Saves local changes. In case of error shows a notification and returns false.
   */
  private boolean save() {
    String errorMessage = mySaver.saveLocalChangesOrError(myRootsToSave);
    if (errorMessage == null) {
      return true;
    }

    VcsNotifier.getInstance(myProject).notifyError(
      COULD_NOT_SAVE_UNCOMMITTED_CHANGES,
      GitBundle.message("save.notification.failed.title", myOperationTitle),
      errorMessage
    );
    return false;
  }

  public void load() {
    if (myLoaded.compareAndSet(false, true)) {
      mySaver.load();
    }
    else {
      LOG.info("The changes were already loaded");
    }
  }
}
