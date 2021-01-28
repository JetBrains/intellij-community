// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.xml.util.XmlStringUtil.wrapInHtml;
import static com.intellij.xml.util.XmlStringUtil.wrapInHtmlTag;
import static git4idea.GitNotificationIdsHolder.COULD_NOT_SAVE_UNCOMMITTED_CHANGES;

/**
 * Executes a Git operation on a number of repositories surrounding it by stash-unstash procedure.
 * I.e. stashes changes, executes the operation and then unstashes it.
 */
public class GitPreservingProcess {

  private static final Logger LOG = Logger.getInstance(GitPreservingProcess.class);

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final Collection<? extends VirtualFile> myRootsToSave;
  @NotNull private final @Nls String myOperationTitle;
  @NotNull private final @Nls String myDestinationName;
  @NotNull private final ProgressIndicator myProgressIndicator;
  @NotNull private final Runnable myOperation;
  @NotNull private final @Nls String myStashMessage;
  @NotNull private final GitChangesSaver mySaver;

  @NotNull private final AtomicBoolean myLoaded = new AtomicBoolean();

  public GitPreservingProcess(@NotNull Project project,
                              @NotNull Git git,
                              @NotNull Collection<? extends VirtualFile> rootsToSave,
                              @Nls @NotNull String operationTitle,
                              @Nls @NotNull String destinationName,
                              @NotNull GitSaveChangesPolicy saveMethod,
                              @NotNull ProgressIndicator indicator,
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
    mySaver = configureSaver(saveMethod);
  }

  public void execute() {
    execute(null);
  }

  public void execute(@Nullable final Computable<Boolean> autoLoadDecision) {
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
            mySaver.notifyLocalChangesAreNotRestored();
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
  @NotNull
  private GitChangesSaver configureSaver(@NotNull GitSaveChangesPolicy saveMethod) {
    GitChangesSaver saver = GitChangesSaver.getSaver(myProject, myGit, myProgressIndicator, myStashMessage, saveMethod);
    MergeDialogCustomizer mergeDialogCustomizer = new MergeDialogCustomizer() {
      @NotNull
      @Override
      public String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
        return wrapInHtml(
          GitBundle.message(
            "restore.conflict.dialog.description.label.text",
            myOperationTitle,
            wrapInHtmlTag(myDestinationName, "code")
          )
        );
      }

      @NotNull
      @Override
      public String getLeftPanelTitle(@NotNull VirtualFile file) {
        return saveMethod.selectBundleMessage(
          GitBundle.message("restore.conflict.diff.dialog.left.stash.title"),
          GitBundle.message("restore.conflict.diff.dialog.left.shelf.title")
        );
      }

      @NotNull
      @Override
      public String getRightPanelTitle(@NotNull VirtualFile file, VcsRevisionNumber revisionNumber) {
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
    try {
      mySaver.saveLocalChanges(myRootsToSave);
      return true;
    } catch (VcsException e) {
      LOG.info("Couldn't save local changes", e);
      VcsNotifier.getInstance(myProject).notifyError(
        COULD_NOT_SAVE_UNCOMMITTED_CHANGES, GitBundle.message("save.notification.failed.title"),
        mySaver.getSaveMethod().selectBundleMessage(
          GitBundle.message("save.notification.failed.stash.text", myOperationTitle, join(e.getMessages())),
          GitBundle.message("save.notification.failed.shelf.text", myOperationTitle, join(e.getMessages()))
        )
      );
      return false;
    }
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
