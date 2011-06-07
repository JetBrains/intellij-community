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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.continuation.ContinuationContext;
import git4idea.GitVcs;
import git4idea.config.GitVcsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Saves and restores uncommitted local changes - it is used before and after the update process.
 * Respects changelists.
 *
 * @author Kirill Likhodedov
 */
public abstract class GitChangesSaver {

  private static final Logger LOG = Logger.getInstance(GitChangesSaver.class);

  protected final Project myProject;
  protected final ChangeListManagerEx myChangeManager;
  protected final ProgressIndicator myProgressIndicator;
  protected final String myStashMessage;
  private final VcsDirtyScopeManager myDirtyScopeManager;

  protected List<LocalChangeList> myChangeLists; // Copy of local change lists - saved before update, used after update to sort changes

  /**
   * Returns an instance of the proper GitChangesSaver depending on the chosen save changes policy.
   * @return {@link GitStashChangesSaver}, {@link GitShelveChangesSaver} or {@link GitDumbChangesSaver}
   */
  public static GitChangesSaver getSaver(Project project, ProgressIndicator progressIndicator, String stashMessage) {
    final GitVcsSettings settings = GitVcsSettings.getInstance(project);
    if (settings == null) {
      return getDefaultSaver(project, progressIndicator, stashMessage);
    }
    switch (settings.updateChangesPolicy()) {
      case STASH: return new GitStashChangesSaver(project, progressIndicator, stashMessage);
      case SHELVE: return new GitShelveChangesSaver(project, progressIndicator, stashMessage);
    }
    return getDefaultSaver(project, progressIndicator, stashMessage);
  }

  // In the case of illegal value in the settings or impossibility to get the settings.
  private static GitChangesSaver getDefaultSaver(Project project, ProgressIndicator progressIndicator, String stashMessage) {
    return new GitStashChangesSaver(project, progressIndicator, stashMessage);
  }

  protected GitChangesSaver(Project project, ProgressIndicator indicator, String stashMessage) {
    myProject = project;
    myProgressIndicator = indicator;
    myStashMessage = stashMessage;
    myChangeManager = (ChangeListManagerEx)ChangeListManagerEx.getInstance(myProject);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
  }

  /**
   * Saves local changes in stash or in shelf.
   * @param rootsToSave Save changes only from these roots.
   */
  public void saveLocalChanges(@Nullable Collection<VirtualFile> rootsToSave) throws VcsException {
    if (rootsToSave == null || rootsToSave.isEmpty()) {
      return;
    }
    myChangeLists = myChangeManager.getChangeListsCopy();
    save(rootsToSave);
  }

  /**
   * Loads local changes from stash or shelf, and sorts the changes back to the change lists they were before update.
   * @param context
   */
  public void restoreLocalChanges(ContinuationContext context) {
    load(getRestoreListsRunnable(), context);
  }

  public void notifyLocalChangesAreNotRestored() {
    if (wereChangesSaved()) {
      LOG.info("Update is incomplete, changes are not restored");
      GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification("Local changes were not restored",
                                                "Before update your uncommitted changes were saved to <a href='saver'>" + getSaverName() + "</a><br/>" +
                                                "Update is not complete, you have unresolved merges in your working tree<br/>" +
                                                "Resolve conflicts, complete update and restore changes manually.", NotificationType.WARNING,
                                                new ShowSavedChangesNotificationListener()).notify(myProject);
    }
  }

  public List<LocalChangeList> getChangeLists() {
    return myChangeLists == null ? myChangeManager.getChangeLists() : myChangeLists;
  }

  /**
   * Utility method - gets {@link FilePath}s of changed files in a single collection.
   */
  public Collection<FilePath> getChangedFiles() {
    final HashSet<FilePath> files = new HashSet<FilePath>();
    for (LocalChangeList changeList : getChangeLists()) {
      for (Change c : changeList.getChanges()) {
        if (c.getAfterRevision() != null) {
          files.add(c.getAfterRevision().getFile());
        }
        if (c.getBeforeRevision() != null) {
          files.add(c.getBeforeRevision().getFile());
        }
      }
    }
    return files;
  }

  /**
   * Saves local changes - specific for chosen save strategy.
   * @param rootsToSave local changes should be saved on these roots.
   */
  protected abstract void save(Collection<VirtualFile> rootsToSave) throws VcsException;

  /**
   * Loads the changes - specific for chosen save strategy.
   * @param restoreListsRunnable
   * @param exceptionConsumer
   */
  protected abstract void load(@Nullable Runnable restoreListsRunnable, ContinuationContext exceptionConsumer);

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

  private Runnable getRestoreListsRunnable() {
    return new Runnable() {
      public void run() {
        if (myChangeLists == null) {
          return;
        }
        LOG.info("restoreChangeLists " + myChangeLists);
        for (LocalChangeList changeList : myChangeLists) {
          final Collection<Change> changes = changeList.getChanges();
          LOG.debug( "restoreProjectChangesAfterUpdate.invokeAfterUpdate changeList: " + changeList.getName() + " changes: " + changes.size());
          if (!changes.isEmpty()) {
            LOG.debug("After restoring files: moving " + changes.size() + " changes to '" + changeList.getName() + "'");
            myChangeManager.moveChangesTo(changeList, changes.toArray(new Change[changes.size()]));
          }
        }
      }
    };
  }

  protected class ShowSavedChangesNotificationListener implements NotificationListener {
    @Override public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equals("saver")) {
        showSavedChanges();
      }
    }
  }
}
