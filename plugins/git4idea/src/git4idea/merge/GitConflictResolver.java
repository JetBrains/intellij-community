// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.merge;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.util.NlsContexts.NotificationContent;
import static com.intellij.openapi.util.NlsContexts.NotificationTitle;
import static com.intellij.openapi.vcs.VcsNotifier.IMPORTANT_ERROR_NOTIFICATION;
import static git4idea.GitNotificationIdsHolder.CANNOT_RESOLVE_CONFLICT;
import static git4idea.GitNotificationIdsHolder.CONFLICT_RESOLVING_ERROR;

/**
 * The class is highly customizable, since the procedure of resolving conflicts is very common in Git operations.
 */
public class GitConflictResolver {

  private static final Logger LOG = Logger.getInstance(GitConflictResolver.class);

  protected final @NotNull Project myProject;
  private final @NotNull Collection<? extends VirtualFile> myRoots;
  private final @NotNull Params myParams;

  /**
   * Customizing parameters - mostly String notification texts, etc.
   */
  public static class Params {
    private boolean reverse;
    private @NotificationTitle String myErrorNotificationTitle = "";
    private @NotificationContent String myErrorNotificationAdditionalDescription = "";
    private String myMergeDescription = "";
    private MergeDialogCustomizer myMergeDialogCustomizer;

    public Params() {
      myMergeDialogCustomizer = new MergeDialogCustomizer() {
        @Override
        public @NotNull String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
          return myMergeDescription;
        }
      };
    }

    public Params(Project project) {
      myMergeDialogCustomizer = new GitDefaultMergeDialogCustomizer(project) {
        @Override
        public @NotNull String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
          if (!StringUtil.isEmpty(myMergeDescription)) {
            return myMergeDescription;
          }
          return super.getMultipleFileMergeDescription(files);
        }
      };
    }

    /**
     * @param reverseMerge specify {@code true} if reverse merge provider has to be used for merging - it is the case of rebase or stash.
     */
    public Params setReverse(boolean reverseMerge) {
      reverse = reverseMerge;
      return this;
    }

    public Params setErrorNotificationTitle(@NotificationTitle String errorNotificationTitle) {
      myErrorNotificationTitle = errorNotificationTitle;
      return this;
    }

    public Params setErrorNotificationAdditionalDescription(@NotificationContent String errorNotificationAdditionalDescription) {
      myErrorNotificationAdditionalDescription = errorNotificationAdditionalDescription;
      return this;
    }

    public Params setMergeDescription(@Nls String mergeDescription) {
      myMergeDescription = mergeDescription;
      return this;
    }

    public Params setMergeDialogCustomizer(MergeDialogCustomizer mergeDialogCustomizer) {
      myMergeDialogCustomizer = mergeDialogCustomizer;
      return this;
    }
  }

  public GitConflictResolver(@NotNull Project project, @NotNull Collection<? extends VirtualFile> roots, @NotNull Params params) {
    myProject = project;
    myRoots = roots;
    myParams = params;
  }

  /**
   * <p>
   *   Goes throw the procedure of merging conflicts via MergeTool for different types of operations.
   *   <ul>
   *     <li>Checks if there are unmerged files. If not, executes {@link #proceedIfNothingToMerge()}</li>
   *     <li>Otherwise shows a {@link com.intellij.openapi.vcs.merge.MultipleFileMergeDialog} where user is able to merge files.</li>
   *     <li>After the dialog is closed, checks if unmerged files remain.
   *         If everything is merged, executes {@link #proceedAfterAllMerged()}. Otherwise shows a notification.</li>
   *   </ul>
   * </p>
   * <p>
   *   If a Git error happens during seeking for unmerged files or in other cases,
   *   the method shows a notification and returns {@code false}.
   * </p>
   *
   * @return {@code true} if there is nothing to merge anymore, {@code false} if unmerged files remain or in the case of error.
   */
  @RequiresBackgroundThread
  public final boolean merge() {
    return merge(false);
  }

  /**
   * This is executed from {@link #merge()} if the initial check tells that there is nothing to merge.
   * In the basic implementation no action is performed, {@code true} is returned.
   * @return Return value is returned from {@link #merge()}
   */
  protected boolean proceedIfNothingToMerge() throws VcsException {
    return true;
  }

  /**
   * This is executed from {@link #merge()} after all conflicts are resolved.
   * In the basic implementation no action is performed, {@code true} is returned.
   * @return Return value is returned from {@link #merge()}
   */
  @RequiresBackgroundThread
  protected boolean proceedAfterAllMerged() throws VcsException {
    return true;
  }

  /**
   * Invoke the merge dialog, but execute nothing after merge is completed.
   */
  @RequiresBackgroundThread
  public final void mergeNoProceed() {
    merge(true);
  }

  @RequiresEdt
  public final void mergeNoProceedInBackground() {
    new Task.Backgroundable(myProject, GitBundle.message("apply.changes.resolving.conflicts.progress.title")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        mergeNoProceed();
      }
    }.queue();
  }

  /**
   * Shows notification that not all conflicts were resolved.
   */
  protected void notifyUnresolvedRemain() {
    notifyWarning(myParams.myErrorNotificationTitle,
                  GitBundle.message("merge.unresolved.conflicts.remaining.notification.body") +
                  myParams.myErrorNotificationAdditionalDescription);
  }

  /**
   * Shows notification that some conflicts were still not resolved - after user invoked the conflict resolver by pressing the link on the
   * notification.
   */
  private void notifyUnresolvedRemainAfterNotification() {
    notifyWarning(GitBundle.message("merge.unresolved.conflicts.remaining.notification.title"),
                  myParams.myErrorNotificationAdditionalDescription);
  }

  protected void notifyWarning(@NotificationTitle @NotNull String title, @NotificationContent @NotNull String content) {
    Notification notification = IMPORTANT_ERROR_NOTIFICATION.createNotification(title, content, NotificationType.WARNING);
    notification.setDisplayId(CANNOT_RESOLVE_CONFLICT);
    notification.addAction(NotificationAction.createSimple(GitBundle.messagePointer("action.NotificationAction.text.resolve"), () -> {
      notification.expire();
      mergeNoProceedInBackground();
    }));
    VcsNotifier.getInstance(myProject).notify(notification);
  }

  private boolean merge(boolean mergeDialogInvokedFromNotification) {
    try {
      final Collection<VirtualFile> initiallyUnmergedFiles = getUnmergedFiles(myRoots);
      if (initiallyUnmergedFiles.isEmpty()) {
        LOG.info("merge: no unmerged files");
        return mergeDialogInvokedFromNotification || proceedIfNothingToMerge();
      }
      else {
        showMergeDialog(initiallyUnmergedFiles);

        final Collection<VirtualFile> unmergedFilesAfterResolve = getUnmergedFiles(myRoots);
        if (unmergedFilesAfterResolve.isEmpty()) {
          LOG.info("merge no more unmerged files");
          return mergeDialogInvokedFromNotification || proceedAfterAllMerged();
        } else {
          LOG.info("mergeFiles unmerged files remain: " + unmergedFilesAfterResolve);
          if (mergeDialogInvokedFromNotification) {
            notifyUnresolvedRemainAfterNotification();
          } else {
            notifyUnresolvedRemain();
          }
        }
      }
    } catch (VcsException e) {
      notifyException(e);
    }
    return false;

  }

  private void showMergeDialog(@NotNull Collection<? extends VirtualFile> initiallyUnmergedFiles) {
    TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.defaultModalityState());
    ApplicationManager.getApplication().invokeAndWait(() -> {
      MergeProvider mergeProvider = new GitMergeProvider(myProject, myParams.reverse);
      AbstractVcsHelper.getInstance(myProject)
        .showMergeDialog(new ArrayList<>(initiallyUnmergedFiles), mergeProvider, myParams.myMergeDialogCustomizer);
    });
  }

  private void notifyException(@NotNull VcsException e) {
    LOG.info("mergeFiles ", e);
    final String description = GitBundle.message(
      "conflict.resolver.unmerged.files.check.error.notification.description.text",
      myParams.myErrorNotificationAdditionalDescription
    );
    VcsNotifier.getInstance(myProject).notifyError(
      CONFLICT_RESOLVING_ERROR, myParams.myErrorNotificationTitle,
      new HtmlBuilder().appendRaw(description).br().appendRaw(e.getLocalizedMessage()).toString()
    );
  }

  /**
   * @return unmerged files in the given Git roots, all in a single collection.
   * @see #getUnmergedFiles(VirtualFile)
   */
  private @NotNull Collection<VirtualFile> getUnmergedFiles(@NotNull Collection<? extends VirtualFile> roots) throws VcsException {
    final Collection<VirtualFile> unmergedFiles = new HashSet<>();
    for (VirtualFile root : roots) {
      unmergedFiles.addAll(getUnmergedFiles(root));
    }
    return unmergedFiles;
  }

  /**
   * @return unmerged files in the given Git root.
   * @see #getUnmergedFiles(Collection)
   */
  private @NotNull Collection<VirtualFile> getUnmergedFiles(@NotNull VirtualFile root) throws VcsException {
    GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForRoot(root);
    if (repository == null) {
      LOG.error("Repository not found for root " + root);
      return Collections.emptyList();
    }

    List<FilePath> files = GitChangeUtils.getUnmergedFiles(repository);
    return ContainerUtil.mapNotNull(files, it -> LocalFileSystem.getInstance().refreshAndFindFileByPath(it.getPath()));
  }
}
