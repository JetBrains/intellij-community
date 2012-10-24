/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.stash;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.continuation.ContinuationContext;
import git4idea.GitPlatformFacade;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.config.GitVcsSettings;
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
  @NotNull protected final GitPlatformFacade myPlatformFacade;
  @NotNull protected final ChangeListManagerEx myChangeManager;
  @NotNull protected final Git myGit;
  @NotNull protected final ProgressIndicator myProgressIndicator;
  @NotNull protected final String myStashMessage;

  protected GitConflictResolver.Params myParams;

  /**
   * Refreshes files changed during save or load.
   */
  public abstract void refresh();

  /**
   * Returns an instance of the proper GitChangesSaver depending on the chosen save changes policy.
   * @return {@link GitStashChangesSaver}, {@link GitShelveChangesSaver} or {@link GitDumbChangesSaver}
   */
  public static GitChangesSaver getSaver(@NotNull Project project, @NotNull GitPlatformFacade platformFacade, @NotNull Git git,
                                         @NotNull ProgressIndicator progressIndicator, @NotNull String stashMessage) {
    final GitVcsSettings settings = GitVcsSettings.getInstance(project);
    if (settings == null) {
      return getDefaultSaver(project, platformFacade, git, progressIndicator, stashMessage);
    }
    switch (settings.updateChangesPolicy()) {
      case STASH: return new GitStashChangesSaver(project, platformFacade, git, progressIndicator, stashMessage);
      case SHELVE: return new GitShelveChangesSaver(project, platformFacade, git, progressIndicator, stashMessage);
    }
    return getDefaultSaver(project, platformFacade, git, progressIndicator, stashMessage);
  }

  // In the case of illegal value in the settings or impossibility to get the settings.
  private static GitChangesSaver getDefaultSaver(@NotNull Project project, @NotNull GitPlatformFacade platformFacade, @NotNull Git git,
                                                 @NotNull ProgressIndicator progressIndicator, @NotNull String stashMessage) {
    return new GitStashChangesSaver(project, platformFacade, git, progressIndicator, stashMessage);
  }

  protected GitChangesSaver(@NotNull Project project, @NotNull GitPlatformFacade platformFacade, @NotNull Git git,
                            @NotNull ProgressIndicator indicator, @NotNull String stashMessage) {
    myProject = project;
    myPlatformFacade = platformFacade;
    myGit = git;
    myProgressIndicator = indicator;
    myStashMessage = stashMessage;
    myChangeManager = platformFacade.getChangeListManager(project);
  }

  /**
   * Saves local changes in stash or in shelf.
   * @param rootsToSave Save changes only from these roots.
   */
  public void saveLocalChanges(@Nullable Collection<VirtualFile> rootsToSave) throws VcsException {
    if (rootsToSave == null || rootsToSave.isEmpty()) {
      return;
    }
    save(rootsToSave);
  }

  /**
   * Loads local changes from stash or shelf, and sorts the changes back to the change lists they were before update.
   * @param context
   */
  public void restoreLocalChanges(ContinuationContext context) {
    load(context);
  }

  public void notifyLocalChangesAreNotRestored() {
    if (wereChangesSaved()) {
      LOG.info("Update is incomplete, changes are not restored");
      GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification("Local changes were not restored",
                                                "Before update your uncommitted changes were saved to <a href='saver'>" + getSaverName() + "</a>.<br/>" +
                                                "Update is not complete, you have unresolved merges in your working tree<br/>" +
                                                "Resolve conflicts, complete update and restore changes manually.", NotificationType.WARNING,
                                                new ShowSavedChangesNotificationListener()).notify(myProject);
    }
  }

  public void setConflictResolverParams(GitConflictResolver.Params params) {
    myParams = params;
  }

  /**
   * Saves local changes - specific for chosen save strategy.
   * @param rootsToSave local changes should be saved on these roots.
   */
  protected abstract void save(Collection<VirtualFile> rootsToSave) throws VcsException;

  /**
   * Loads the changes - specific for chosen save strategy.
   * @param exceptionConsumer
   */
  protected abstract void load(ContinuationContext exceptionConsumer);

  /**
   * @return true if there were local changes to save.
   */
  protected abstract boolean wereChangesSaved();

  /**
   * @return name of the save capability provider - stash or shelf.
   */
  public abstract String getSaverName();

  /**
   * Show the saved local changes in the proper viewer.
   */
  protected abstract void showSavedChanges();

  /**
   * The right panel title of the merge conflict dialog: changes that came from update.
   */
  @NotNull
  protected static String getConflictRightPanelTitle() {
    return "Changes from remote";
  }

  /**
   * The left panel title of the merge conflict dialog: changes that were preserved in this saver during update.
   */
  @NotNull
  protected static String getConflictLeftPanelTitle() {
    return "Your uncommitted changes";
  }

  protected class ShowSavedChangesNotificationListener implements NotificationListener {
    @Override public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equals("saver")) {
        showSavedChanges();
      }
    }
  }
}

