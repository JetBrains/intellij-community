package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public enum ChangelistConflictResolution {

  SHELVE {
    @Override
    public boolean resolveConflict(final Project project, Collection<Change> changes) {
      LocalChangeList changeList = getManager(project).getChangeList(changes.iterator().next());
      assert changeList != null;
      try {
        ShelveChangesManager.getInstance(project).shelveChanges(changes, changeList.getName());
        return true;
      }
      catch (final Exception ex) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showErrorDialog(project, VcsBundle.message("create.patch.error.title", ex.getMessage()), CommonBundle.getErrorTitle());
          }
        }, ModalityState.NON_MODAL);
        return false;
      }
    }},

  MOVE {
    @Override
    public boolean resolveConflict(Project project, Collection<Change> changes) {
      final ChangeListManagerImpl manager = getManager(project);
      manager.moveChangesTo(manager.getDefaultChangeList(), changes.toArray(new Change[changes.size()]));
      return true;
    }},

  SWITCH {
    @Override
    public boolean resolveConflict(Project project, Collection<Change> changes) {
      LocalChangeList changeList = getManager(project).getChangeList(changes.iterator().next());
      assert changeList != null;
      getManager(project).setDefaultChangeList(changeList);
      return true;
    }},

  IGNORE {
    @Override
    public boolean resolveConflict(Project project, Collection<Change> changes) {
      ChangeListManagerImpl manager = getManager(project);
      for (Change change : changes) {
        VirtualFile file = change.getVirtualFile();
        if (file != null) {
          manager.getConflictTracker().ignoreConflict(file, true);
        }
      }
      return true;
    }};

  public abstract boolean resolveConflict(Project project, Collection<Change> changes);

  private static ChangeListManagerImpl getManager(Project project) {
    return (ChangeListManagerImpl)ChangeListManager.getInstance(project);
  }
}
