// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.readOnlyHandler.FileListRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionListModel;
import org.jetbrains.annotations.NotNull;

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
  private JLabel myListTitle;
  private JList<VirtualFile> myFileList;

  private final Project myProject;

  public ChangelistConflictDialog(Project project, List<ChangeList> changeLists, List<VirtualFile> conflicts) {
    super(project);
    myProject = project;

    setTitle(VcsBundle.message("dialog.title.resolve.changelist.conflict"));

    boolean dirsOnly = conflicts.stream().allMatch(VirtualFile::isDirectory);
    int size = conflicts.size();

    String text = (dirsOnly
                   ? VcsBundle.message("changes.directory.does.not.belong.to.the.active.changelist", size)
                   : VcsBundle.message("changes.file.does.not.belong.to.the.active.changelist", size));

    myListTitle.setText(text);

    myFileList.setCellRenderer(new FileListRenderer());
    myFileList.setModel(new CollectionListModel<>(conflicts));

    ChangelistConflictResolution resolution = ChangelistConflictTracker.getInstance(myProject).getOptions().LAST_RESOLUTION;
    LocalChangeList defaultChangeList = ChangeListManager.getInstance(myProject).getDefaultChangeList();

    if (changeLists.size() > 1) {
      mySwitchToChangelistRadioButton.setEnabled(false);
      if (resolution == ChangelistConflictResolution.SWITCH) {
        resolution = ChangelistConflictResolution.IGNORE;
      }
    }
    mySwitchToChangelistRadioButton.setText(VcsBundle.message("switch.to.changelist", changeLists.iterator().next().getName()));
    myMoveChangesToActiveRadioButton.setText(VcsBundle.message("move.to.changelist", defaultChangeList.getName()));

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
  protected Action @NotNull [] createLeftSideActions() {
    return new Action[] { new AbstractAction(VcsBundle.message("changes.configure")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        ShowSettingsUtil.getInstance().editConfigurable(myPanel, new ChangelistConflictConfigurable(myProject));
      }
    }};
  }

  @Override
  protected String getHelpId() {
    return "project.propVCSSupport.ChangelistConflict";
  }
}
