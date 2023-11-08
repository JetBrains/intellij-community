// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
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
    if (saveMethod == GitSaveChangesPolicy.SHELVE) {
      return new GitShelveChangesSaver(project, git, progressIndicator, stashMessage);
    }
    return new GitStashChangesSaver(project, git, progressIndicator, stashMessage);
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

  public void notifyLocalChangesAreNotRestored() {
    if (wereChangesSaved()) {
      LOG.info("Update is incomplete, changes are not restored");
      VcsNotifier.getInstance(myProject).notifyImportantWarning(
        LOCAL_CHANGES_NOT_RESTORED, GitBundle.message("restore.notification.failed.title"),
        getSaveMethod().selectBundleMessage(
          GitBundle.message("restore.notification.failed.stash.message"),
          GitBundle.message("restore.notification.failed.shelf.message")
        ),
        new ShowSavedChangesNotificationListener()
      );
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

  protected final class ShowSavedChangesNotificationListener implements NotificationListener {
    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equals("saver")) {
        showSavedChanges();
      }
    }
  }
}

