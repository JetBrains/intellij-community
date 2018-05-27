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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
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
  @NotNull protected final ChangeListManagerEx myChangeManager;
  @NotNull protected final Git myGit;
  @NotNull protected final ProgressIndicator myProgressIndicator;
  @NotNull protected final String myStashMessage;

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
                                         @NotNull GitVcsSettings.UpdateChangesPolicy saveMethod) {
    if (saveMethod == GitVcsSettings.UpdateChangesPolicy.SHELVE) {
      return new GitShelveChangesSaver(project, git, progressIndicator, stashMessage);
    }
    return new GitStashChangesSaver(project, git, progressIndicator, stashMessage);
  }

  protected GitChangesSaver(@NotNull Project project, @NotNull Git git,
                            @NotNull ProgressIndicator indicator, @NotNull String stashMessage) {
    myProject = project;
    myGit = git;
    myProgressIndicator = indicator;
    myStashMessage = stashMessage;
    myChangeManager = ChangeListManagerImpl.getInstanceImpl(project);
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

  public void notifyLocalChangesAreNotRestored() {
    if (wereChangesSaved()) {
      LOG.info("Update is incomplete, changes are not restored");
      VcsNotifier.getInstance(myProject).notifyImportantWarning("Local changes were not restored",
                                                                "Before update your uncommitted changes were saved to <a href='saver'>" +
                                                                getSaverName() +
                                                                "</a>.<br/>" +
                                                                "Update is not complete, you have unresolved merges in your working tree<br/>" +
                                                                "Resolve conflicts, complete update and restore changes manually.",
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
  protected abstract void save(Collection<VirtualFile> rootsToSave) throws VcsException;

  /**
   * Loads the changes - specific for chosen save strategy.
   */
  public abstract void load();

  /**
   * @return true if there were local changes to save.
   */
  public abstract boolean wereChangesSaved();

  /**
   * @return name of the save capability provider - stash or shelf.
   */
  public abstract String getSaverName();

  /**
   * @return the name of the saving operation: stash or shelve.
   */
  @NotNull
  public abstract String getOperationName();

  /**
   * Show the saved local changes in the proper viewer.
   */
  public abstract void showSavedChanges();

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

