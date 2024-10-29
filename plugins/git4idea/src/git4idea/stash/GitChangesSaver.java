// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash;

import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static git4idea.GitNotificationIdsHolder.LOCAL_CHANGES_NOT_RESTORED;

/**
 * Saves and restores uncommitted local changes - it is used before and after the update process.
 * Respects changelists.
 *
 * @author Kirill Likhodedov
 */
public abstract class GitChangesSaver {
  private static final Logger LOG = Logger.getInstance(GitChangesSaver.class);

  protected final @NotNull Project myProject;
  protected final @NotNull Git myGit;
  protected final @NotNull ProgressIndicator myProgressIndicator;
  protected final @NotNull String myStashMessage;
  private final @NotNull GitSaveChangesPolicy mySaveMethod;

  protected GitConflictResolver.Params myParams;

  /**
   * Returns an instance of the proper GitChangesSaver depending on the given save changes policy.
   *
   * @return {@link GitStashChangesSaver} or {@link GitShelveChangesSaver}.
   */
  public static @NotNull GitChangesSaver getSaver(@NotNull Project project,
                                                  @NotNull Git git,
                                                  @NotNull ProgressIndicator progressIndicator,
                                                  @NotNull @Nls String stashMessage,
                                                  @NotNull GitSaveChangesPolicy saveMethod) {
    return getSaver(project, git, progressIndicator, stashMessage, saveMethod, true);
  }

  @ApiStatus.Internal
  public static @NotNull GitChangesSaver getSaver(@NotNull Project project,
                                                  @NotNull Git git,
                                                  @NotNull ProgressIndicator progressIndicator,
                                                  @NotNull @Nls String stashMessage,
                                                  @NotNull GitSaveChangesPolicy saveMethod,
                                                  boolean reportLocalHistoryActivity) {
    if (saveMethod == GitSaveChangesPolicy.SHELVE) {
      GitShelveChangesSaver shelveSaver = new GitShelveChangesSaver(project, git, progressIndicator, stashMessage);
      shelveSaver.setReportLocalHistoryActivity(reportLocalHistoryActivity);
      return shelveSaver;
    }
    GitStashChangesSaver stashSaver = new GitStashChangesSaver(project, git, progressIndicator, stashMessage);
    stashSaver.setReportLocalHistoryActivity(reportLocalHistoryActivity);
    return stashSaver;
  }

  protected GitChangesSaver(@NotNull Project project,
                            @NotNull Git git,
                            @NotNull ProgressIndicator indicator,
                            @NotNull GitSaveChangesPolicy saveMethod,
                            @NotNull String stashMessage) {
    myProject = project;
    myGit = git;
    myProgressIndicator = indicator;
    mySaveMethod = saveMethod;
    myStashMessage = stashMessage;
  }

  /**
   * Saves local changes in stash or in shelf.
   *
   * @param rootsToSave Save changes only from these roots.
   */
  public void saveLocalChanges(@Nullable Collection<? extends VirtualFile> rootsToSave) throws VcsException {
    if (rootsToSave == null || rootsToSave.isEmpty()) {
      return;
    }
    save(rootsToSave);
  }

  public @Nullable @Nls String saveLocalChangesOrError(@Nullable Collection<? extends VirtualFile> rootsToSave) {
    try {
      saveLocalChanges(rootsToSave);
      return null;
    }
    catch (VcsException e) {
      LOG.warn(e);

      String message = getSaveMethod().selectBundleMessage(
        GitBundle.message("save.notification.failed.stash.text"),
        GitBundle.message("save.notification.failed.shelf.text")
      );
      return new HtmlBuilder().append(message).br().appendRaw(e.getMessage()).toString();
    }
  }

  public void notifyLocalChangesAreNotRestored(@NotNull @Nls String operationName) {
    if (wereChangesSaved()) {
      LOG.info("Local changes are not restored");
      VcsNotifier.importantNotification()
        .createNotification(GitBundle.message("restore.notification.failed.title"),
                            getSaveMethod().selectBundleMessage(
                              GitBundle.message("restore.notification.failed.stash.message", operationName),
                              GitBundle.message("restore.notification.failed.shelf.message", operationName)
                            ),
                            NotificationType.WARNING)
        .setDisplayId(LOCAL_CHANGES_NOT_RESTORED)
        .addAction(NotificationAction.createSimple(
          GitBundle.messagePointer("restore.notification.failed.show.changes.action"), () -> {
            showSavedChanges();
          }))
        .notify(myProject);
    }
  }

  public void setConflictResolverParams(GitConflictResolver.Params params) {
    myParams = params;
  }

  /**
   * Saves local changes - specific for chosen save strategy.
   *
   * @param rootsToSave local changes should be saved on these roots.
   */
  protected abstract void save(Collection<? extends VirtualFile> rootsToSave) throws VcsException;

  /**
   * Loads the changes - specific for chosen save strategy.
   */
  public abstract void load();

  /**
   * @return true if there were local changes to save.
   */
  public abstract boolean wereChangesSaved();

  public @NotNull GitSaveChangesPolicy getSaveMethod() {
    return mySaveMethod;
  }

  /**
   * Show the saved local changes in the proper viewer.
   */
  public abstract void showSavedChanges();

  /**
   * The right panel title of the merge conflict dialog: changes that came from update.
   */
  protected static @NotNull String getConflictRightPanelTitle() {
    return GitBundle.message("save.load.conflict.dialog.diff.right.title");
  }

  /**
   * The left panel title of the merge conflict dialog: changes that were preserved in this saver during update.
   */
  protected static @NotNull String getConflictLeftPanelTitle() {
    return GitBundle.message("save.load.conflict.dialog.diff.left.title");
  }
}

