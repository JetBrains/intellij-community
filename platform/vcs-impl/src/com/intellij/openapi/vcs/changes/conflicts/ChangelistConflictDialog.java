package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.readOnlyHandler.FileListRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionListModel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class ChangelistConflictDialog extends DialogWrapper {

  private JPanel myPanel;

  private JRadioButton myShelveChangesRadioButton;
  private JRadioButton myMoveChangesToActiveRadioButton;
  private JRadioButton mySwitchToChangelistRadioButton;
  private JRadioButton myIgnoreRadioButton;
  private JList myFileList;

  private final Project myProject;

  public ChangelistConflictDialog(Project project, List<ChangeList> changeLists, List<VirtualFile> conflicts) {
    super(project);
    myProject = project;

    setTitle("Resolve Changelist Conflict");

    myFileList.setCellRenderer(new FileListRenderer());
    myFileList.setModel(new CollectionListModel(conflicts));

    ChangeListManagerImpl manager = ChangeListManagerImpl.getInstanceImpl(myProject);
    ChangelistConflictResolution resolution = manager.getConflictTracker().getOptions().LAST_RESOLUTION;

    if (changeLists.size() > 1) {
      mySwitchToChangelistRadioButton.setEnabled(false);
      if (resolution == ChangelistConflictResolution.SWITCH) {
        resolution = ChangelistConflictResolution.IGNORE;
      }
    }
    mySwitchToChangelistRadioButton.setText(VcsBundle.message("switch.to.changelist", changeLists.iterator().next().getName()));
    myMoveChangesToActiveRadioButton.setText(VcsBundle.message("move.to.changelist", manager.getDefaultChangeList().getName()));
    
    switch (resolution) {

      case SHELVE:
        myShelveChangesRadioButton.setSelected(true);
        break;
      case MOVE:
        myMoveChangesToActiveRadioButton.setSelected(true);
        break;
      case SWITCH:
        mySwitchToChangelistRadioButton.setSelected(true) ;
        break;
      case IGNORE:
        myIgnoreRadioButton.setSelected(true);
        break;
    }
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public ChangelistConflictResolution getResolution() {
    if (myShelveChangesRadioButton.isSelected())
      return ChangelistConflictResolution.SHELVE;
    if (myMoveChangesToActiveRadioButton.isSelected())
      return ChangelistConflictResolution.MOVE;
    if (mySwitchToChangelistRadioButton.isSelected())
      return ChangelistConflictResolution.SWITCH;
    return ChangelistConflictResolution.IGNORE;
  }

  @Override
  protected Action[] createLeftSideActions() {
    return new Action[] { new AbstractAction("&Configure...") {
      public void actionPerformed(ActionEvent e) {
        ChangeListManagerImpl manager = (ChangeListManagerImpl)ChangeListManager.getInstance(myProject);
        ShowSettingsUtil.getInstance().editConfigurable(myPanel, new ChangelistConflictConfigurable(manager));
      }
    }};
  }
}
