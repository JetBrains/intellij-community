// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;

/**
 * Saves and restores uncommitted local changes - it is used before and after the update process.
 * Respects changelists.
 *
 * @author Kirill Likhodedov
 */
public abstract class GitChangesSaver {
  private static final Logger LOG = Logger.getInstance(GitChangesSaver.class);

  @NotNull protected final Project myProject;
  @NotNull protected final Git myGit;
  @NotNull protected final ProgressIndicator myProgressIndicator;
  @NotNull protected final String myStashMessage;
  @NotNull private final GitSaveChangesPolicy mySaveMethod;

  protected GitConflictResolver.Params myParams;

  /**
   * Returns an instance of the proper GitChangesSaver depending on the given save changes policy.
   * @return {@link GitStashChangesSaver} or {@link GitShelveChangesSaver}.
   */
  @NotNull
  public static GitChangesSaver getSaver(@NotNull Project project,
                                         @NotNull Git git,
                                         @NotNull ProgressIndicator progressIndicator,
                                         @NotNull String stashMessage,
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
        GitBundle.getString("restore.notification.failed.title"),
        getSaveMethod().selectBundleMessage(
          GitBundle.getString("restore.notification.failed.stash.message"),
          GitBundle.getString("restore.notification.failed.shelf.message")
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

  @NotNull
  public GitSaveChangesPolicy getSaveMethod() {
    return mySaveMethod;
  }

  /**
   * Show the saved local changes in the proper viewer.
   */
  public abstract void showSavedChanges();

  /**
   * The right panel title of the merge conflict dialog: changes that came from update.
   */
  @NotNull
  protected static String getConflictRightPanelTitle() {
    return GitBundle.getString("save.load.conflict.dialog.diff.right.title");
  }

  /**
   * The left panel title of the merge conflict dialog: changes that were preserved in this saver during update.
   */
  @NotNull
  protected static String getConflictLeftPanelTitle() {
    return GitBundle.getString("save.load.conflict.dialog.diff.left.title");
  }

  protected final class ShowSavedChangesNotificationListener implements NotificationListener {
    @Override public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equals("saver")) {
        showSavedChanges();
      }
    }
  }
}

