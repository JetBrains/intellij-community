package org.zmlx.hg4idea;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TitlePanel;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.util.PairConsumer;
import com.intellij.util.ui.ConfirmationDialog;
import gnu.trove.THashSet;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * HgRemoveCheckinHandler scans the changes which are ready for commit
 * for files, which were deleted on the file system, but not from the VCS,
 * and proposes a dialog to select files which are to be removed from the VCS.
 */
public class HgRemoveCheckinHandler extends CheckinHandler {

  private final CheckinProjectPanel myCheckinPanel;
  private final Project myProject;

  public HgRemoveCheckinHandler(CheckinProjectPanel checkinPanel) {
    myCheckinPanel = checkinPanel;
    myProject = checkinPanel.getProject();
  }

  @Override
  public ReturnResult beforeCheckin(CommitExecutor executor, final PairConsumer<Object, Object> additionalDataConsumer) {
    // find missing changes
    final List<Change> missingChanges = new LinkedList<Change>();
    for (Change c : myCheckinPanel.getSelectedChanges()) {
      if (c.getFileStatus() == FileStatus.DELETED_FROM_FS) {
        missingChanges.add(c);
      }
    }

    if (missingChanges.isEmpty()) {
      return ReturnResult.COMMIT;
    }

    // show a simple confirmation for 1 missing change, or more complex dialog for 2 or more changes
    final Collection<Change> changesToRemove = new THashSet<Change>();
    VcsShowConfirmationOption confirmation = ProjectLevelVcsManager.getInstance(myProject).getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, HgVcs.getInstance(myProject));
    if (missingChanges.size() == 1) {
      if (ConfirmationDialog
        .requestForConfirmation(confirmation, myProject,
                                HgVcsMessages.message("hg4idea.remove.commit.single.body", missingChanges.get(0).getBeforeRevision().getFile().getPresentableUrl()),
                                HgVcsMessages.message("hg4idea.remove.single.title"), Messages.getQuestionIcon())) {
        changesToRemove.add(missingChanges.get(0));
      }
    } else {
      final SelectMissingChangesDialog dialog = new SelectMissingChangesDialog(myProject, missingChanges, confirmation);
      dialog.show();
      if (dialog.isOK()) {
        changesToRemove.addAll(dialog.getSelectedChanges());
      } else {
        return ReturnResult.CANCEL;
      }
    }

    if (!changesToRemove.isEmpty()) {
      removeChangesAndUpdateCommitted(changesToRemove, additionalDataConsumer);
    }
    return ReturnResult.COMMIT;
  }

  private void removeChangesAndUpdateCommitted(final Collection<Change> changesToRemove,
                                               final PairConsumer<Object, Object> additionalDataConsumer) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final List<FilePath> filepathsToRemove = new ArrayList<FilePath>(changesToRemove.size());
        for (Change c : changesToRemove) {
          filepathsToRemove.add(c.getBeforeRevision().getFile());
        }
        HgUtil.removeFilesFromVcs(myProject, filepathsToRemove);
        additionalDataConsumer.consume(HgVcs.getInstance(myProject), changesToRemove);
      }
    }, HgVcsMessages.message("hg4idea.remove.progress"), true, myProject);
  }

  private class SelectMissingChangesDialog extends AbstractSelectFilesDialog<Change> {

    public SelectMissingChangesDialog(final Project project, List<Change> originalFiles, VcsShowConfirmationOption confirmation) {
      super(project, false, confirmation, null);
      myFileList = new ChangesTreeList<Change>(project, originalFiles, true, true, null, null) {
        protected DefaultTreeModel buildTreeModel(final List<Change> changes, ChangeNodeDecorator changeNodeDecorator) {
          return new TreeModelBuilder(project, false).buildModel(changes, changeNodeDecorator);
        }

        protected List<Change> getSelectedObjects(final ChangesBrowserNode node) {
          return node.getAllChangesUnder();
        }

        protected Change getLeadSelectedObject(final ChangesBrowserNode node) {
          final Object o = node.getUserObject();
          if (o instanceof Change) {
            return (Change) o;
          }
          return null;
        }
      };
      myFileList.setChangesToDisplay(originalFiles);
      myPanel.add(myFileList, BorderLayout.CENTER);
      setOKButtonText(HgVcsMessages.message("hg4idea.remove.button.ok"));
      setTitle(HgVcsMessages.message("hg4idea.remove.multiple.title"));
      init();
    }

    public Collection<Change> getSelectedChanges() {
      return myFileList.getIncludedChanges();
    }

    protected JComponent createTitlePane() {
      return new TitlePanel(HgVcsMessages.message("hg4idea.remove.commit.multiple.title"), HgVcsMessages.message(
        "hg4idea.remove.commit.multiple.description"));
    }

  }

}