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
package git4idea.update;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.config.GitVcsSettings;
import git4idea.convert.GitFileSeparatorConverter;
import git4idea.merge.GitMergeConflictResolver;
import git4idea.ui.GitUIUtil;
import git4idea.ui.GitUnstashDialog;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Kirill Likhodedov
 */
public class GitStashChangesSaver extends GitChangesSaver {

  private static final Logger LOG = Logger.getInstance(GitStashChangesSaver.class);
  private final Set<VirtualFile> myStashedRoots = new HashSet<VirtualFile>(); // save stashed roots to unstash only them

  GitStashChangesSaver(Project project, ProgressIndicator progressIndicator, String stashMessage) {
    super(project, progressIndicator, stashMessage);
  }

  @Override
  protected void save(Collection<VirtualFile> rootsToSave) throws VcsException {
    Map<VirtualFile, Collection<Change>> changes = groupChangesByRoots(rootsToSave);
    convertSeparatorsIfNeeded(changes);
    stash(changes.keySet());
  }

  @Override
  protected void load() throws VcsException {
    for (VirtualFile root : myStashedRoots) {
      loadRoot(root);
    }
    final List<File> files = ObjectsConvertor.fp2jiof(getChangedFiles());
    LocalFileSystem.getInstance().refreshIoFiles(files);
  }

  @Override
  protected boolean wereChangesSaved() {
    return !myStashedRoots.isEmpty();
  }

  @Override protected String getSaverName() {
    return "stash";
  }

  @Override protected void showSavedChanges() {
    GitUnstashDialog.showUnstashDialog(myProject, new ArrayList<VirtualFile>(myStashedRoots), myStashedRoots.iterator().next(), new HashSet<VirtualFile>());
  }

  private void stash(Collection<VirtualFile> roots) throws VcsException {
    for (VirtualFile root : roots) {
      final String message = GitHandlerUtil.formatOperationName("Stashing changes from", root);
      LOG.info(message);
      myProgressIndicator.setText(message);
      if (GitStashUtils.saveStash(myProject, root, myStashMessage)) {
        myStashedRoots.add(root);
      }
    }
  }

  private void convertSeparatorsIfNeeded(Map<VirtualFile, Collection<Change>> changes) throws VcsException {
    GitVcsSettings settings = GitVcsSettings.getInstance(myProject);
    if (settings != null) {
      List<VcsException> exceptions = new ArrayList<VcsException>(1);
      GitFileSeparatorConverter.convertSeparatorsIfNeeded(myProject, settings, changes, exceptions);
      if (!exceptions.isEmpty()) {
        throw exceptions.get(0);
      }
    }
  }

  private void loadRoot(final VirtualFile root) throws VcsException {
    myProgressIndicator.setText(GitHandlerUtil.formatOperationName("Unstashing changes to", root));
    final GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.STASH);
    handler.setNoSSH(true);
    handler.addParameters("pop");

    final AtomicBoolean conflict = new AtomicBoolean();
    handler.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (line.contains("Merge conflict")) {
          conflict.set(true);
        }
      }
    });

    final GitTask task = new GitTask(myProject, handler, "git stash pop");
    task.setExecuteResultInAwt(false);
    task.executeInBackground(true, new GitTaskResultHandlerAdapter() {
      @Override protected void onSuccess() {
      }

      @Override protected void onCancel() {
        Notifications.Bus.notify(new Notification(GitVcs.NOTIFICATION_GROUP_ID, "Unstash cancelled",
                                                  "You may view the stashed changes <a href='saver'>here</a>", NotificationType.WARNING,
                                                  new ShowSavedChangesNotificationListener()), myProject);
      }

      @Override protected void onFailure() {
        if (conflict.get()) {
          new GitMergeConflictResolver(myProject, true, "Uncommitted changes that were stashed before update have conflicts with updated files.",
                                       "Can't update", "").mergeFiles(Collections.singleton(root));
        } else {
          GitUIUtil.notifyImportantError(myProject, "Couldn't unstash", "<br/>" + GitUIUtil.stringifyErrors(handler.errors()));
        }
      }
    });

  }

  // Sort changes from myChangesLists by their git roots.
  // And use only supplied roots, ignoring changes from other roots.
  private Map<VirtualFile, Collection<Change>> groupChangesByRoots(Collection<VirtualFile> rootsToSave) {
    final Map<VirtualFile, Collection<Change>> sortedChanges = new HashMap<VirtualFile, Collection<Change>>();
    for (LocalChangeList l : myChangeLists) {
      final Collection<Change> changeCollection = l.getChanges();
      for (Change c : changeCollection) {
        if (c.getAfterRevision() != null) {
          storeChangeInMap(sortedChanges, c, c.getAfterRevision(), rootsToSave);
        } else if (c.getBeforeRevision() != null) {
          storeChangeInMap(sortedChanges, c, c.getBeforeRevision(), rootsToSave);
        }
      }
    }
    return sortedChanges;
  }

  private static void storeChangeInMap(Map<VirtualFile, Collection<Change>> sortedChanges,
                                       Change c,
                                       ContentRevision contentRevision,
                                       Collection<VirtualFile> rootsToSave) {
    final VirtualFile root = GitUtil.getGitRootOrNull(contentRevision.getFile());
    if (root != null && rootsToSave.contains(root)) {
      Collection<Change> changes = sortedChanges.get(root);
      if (changes == null) {
        changes = new ArrayList<Change>();
        sortedChanges.put(root, changes);
      }
      changes.add(c);
    }
  }

}
