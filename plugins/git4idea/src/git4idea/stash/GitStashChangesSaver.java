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
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.ui.UIUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.config.GitVcsSettings;
import git4idea.convert.GitFileSeparatorConverter;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitMergeConflictResolver;
import git4idea.ui.GitUIUtil;
import git4idea.ui.GitUnstashDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.notification.NotificationType.WARNING;

/**
 * @author Kirill Likhodedov
 */
public class GitStashChangesSaver extends GitChangesSaver {

  private static final Logger LOG = Logger.getInstance(GitStashChangesSaver.class);
  private final Set<VirtualFile> myStashedRoots = new HashSet<VirtualFile>(); // save stashed roots to unstash only them

  public GitStashChangesSaver(Project project, ProgressIndicator progressIndicator, String stashMessage) {
    super(project, progressIndicator, stashMessage);
  }

  @Override
  protected void save(Collection<VirtualFile> rootsToSave) throws VcsException {
    LOG.info("save " + rootsToSave);
    Map<VirtualFile, Collection<Change>> changes = groupChangesByRoots(rootsToSave);
    convertSeparatorsIfNeeded(changes);
    stash(changes.keySet());
  }

  @Override
  protected void load(@Nullable final Runnable restoreListsRunnable, ContinuationContext context) {
    for (VirtualFile root : myStashedRoots) {
      try {
        loadRoot(root);
      }
      catch (VcsException e) {
        context.handleException(e);
        return;
      }
    }
    final List<File> files = ObjectsConvertor.fp2jiof(getChangedFiles());
    LocalFileSystem.getInstance().refreshIoFiles(files);
    if (restoreListsRunnable != null) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        public void run() {
          myChangeManager.invokeAfterUpdate(restoreListsRunnable, InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE,
                                            GitBundle.getString("update.restoring.change.lists"), ModalityState.NON_MODAL);
        }
      });
    }
  }

    @Override
  protected boolean wereChangesSaved() {
    return !myStashedRoots.isEmpty();
  }

  @Override public String getSaverName() {
    return "stash";
  }

  @Override protected void showSavedChanges() {
    GitUnstashDialog.showUnstashDialog(myProject, new ArrayList<VirtualFile>(myStashedRoots), myStashedRoots.iterator().next(), new HashSet<VirtualFile>());
  }

  private void stash(Collection<VirtualFile> roots) throws VcsException {
    for (VirtualFile root : roots) {
      final String message = GitHandlerUtil.formatOperationName("Stashing changes from", root);
      LOG.info(message);
      final String oldProgressTitle = myProgressIndicator.getText();
      myProgressIndicator.setText(message);
      if (GitStashUtils.saveStash(myProject, root, myStashMessage)) {
        myStashedRoots.add(root);
      }
      myProgressIndicator.setText(oldProgressTitle);
    }
  }

  private void convertSeparatorsIfNeeded(Map<VirtualFile, Collection<Change>> changes) throws VcsException {
    LOG.info("convertSeparatorsIfNeeded ");
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
    LOG.info("loadRoot " + root);
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

    final GitTask task = new GitTask(myProject, handler, "Unstashing uncommitted changes");
    task.setProgressIndicator(myProgressIndicator);
    final AtomicBoolean failure = new AtomicBoolean();
    task.executeInBackground(true, new GitTaskResultHandlerAdapter() {
      @Override protected void onSuccess() {
      }

      @Override protected void onCancel() {
        Notifications.Bus.notify(new Notification(GitVcs.NOTIFICATION_GROUP_ID, "Unstash cancelled",
                                                  "You may view the stashed changes <a href='saver'>here</a>", WARNING,
                                                  new ShowSavedChangesNotificationListener()), myProject);
      }

      @Override protected void onFailure() {
        failure.set(true);
      }
    });

    if (failure.get()) {
      if (conflict.get()) {
        boolean conflictsResolved = new UnstashConflictResolver().merge(Collections.singleton(root));
        if (conflictsResolved) {
          LOG.info("loadRoot " + root + " conflicts resolved, dropping stash");
          dropStash(root);
        }
      } else {
        LOG.info("unstash failed " + handler.errors());
        GitUIUtil.notifyImportantError(myProject, "Couldn't unstash", "<br/>" + GitUIUtil.stringifyErrors(handler.errors()));
      }
    }
  }

  // drops stash (after completing conflicting merge during unstashing), shows a warning in case of error
  private void dropStash(VirtualFile root) {
    final GitSimpleHandler handler = new GitSimpleHandler(myProject, root, GitCommand.STASH);
    handler.setNoSSH(true);
    handler.addParameters("drop");
    String output = null;
    try {
      output = handler.run();
    } catch (VcsException e) {
      LOG.info("dropStash " + output, e);
      GitUIUtil.notifyMessage(myProject, "Couldn't drop stash",
                              "Couldn't drop stash after resolving conflicts.<br/>Please drop stash manually.",
                              WARNING, false, handler.errors());
    }
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

  private class UnstashConflictResolver extends GitMergeConflictResolver {
    public UnstashConflictResolver() {
      super(GitStashChangesSaver.this.myProject, true, new UnstashMergeDialogCustomizer(), "Local changes were not restored", "");
    }

    @Override
    protected void notifyUnresolvedRemain(final Collection<VirtualFile> roots) {
      Notifications.Bus.notify(new Notification(GitVcs.IMPORTANT_ERROR_NOTIFICATION, "Local changes were restored with conflicts",
                                                "Before update your uncommitted changes were saved to <a href='saver'>" +
                                                getSaverName() +
                                                "</a><br/>" +
                                                "Unstash is not complete, you have unresolved merges in your working tree<br/>" +
                                                "<a href='resolve'>Resolve</a> conflicts and drop the stash.",
                                                NotificationType.WARNING, new NotificationListener() {
          @Override
          public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              if (event.getDescription().equals("saver")) {
                // we don't use #showSavedChanges to specify unmerged root first
                GitUnstashDialog.showUnstashDialog(myProject, new ArrayList<VirtualFile>(myStashedRoots), roots.iterator().next(),
                                                   new HashSet<VirtualFile>());
              } else if (event.getDescription().equals("resolve")) {
                new UnstashConflictResolver().justMerge(roots);
              }
            }
          }
      }));
    }

  }

  private static class UnstashMergeDialogCustomizer extends MergeDialogCustomizer {

    @Override
    public String getMultipleFileMergeDescription(Collection<VirtualFile> files) {
      return "Uncommitted changes that were stashed before update have conflicts with updated files.";
    }

    @Override
    public String getLeftPanelTitle(VirtualFile file) {
      return "Your uncommitted changes";
    }

    @Override
    public String getRightPanelTitle(VirtualFile file, VcsRevisionNumber lastRevisionNumber) {
      return "Changes from remote";
    }
  }
}
