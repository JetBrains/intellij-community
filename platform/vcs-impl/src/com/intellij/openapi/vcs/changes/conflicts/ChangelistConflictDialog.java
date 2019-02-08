// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.readOnlyHandler.FileListRenderer;
import com.intellij.openapi.vcs.readOnlyHandler.ReadOnlyStatusDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class ChangelistConflictDialog extends DialogWrapper {

  private static final int SECONDS_TO_LOCK_OK = 2;
  private JPanel myPanel;

  private JRadioButton myShelveChangesRadioButton;
  private JRadioButton myMoveChangesToActiveRadioButton;
  private JRadioButton mySwitchToChangelistRadioButton;
  private JRadioButton myIgnoreRadioButton;
  private JLabel myListTitle;
  private JList<VirtualFile> myFileList;

  private final Project myProject;
  private final int mySecondsUntilOkEnabled;

  public ChangelistConflictDialog(Project project, List<ChangeList> changeLists, List<VirtualFile> conflicts) {
    super(project);
    myProject = project;

    setTitle("Resolve Changelist Conflict");

    myListTitle.setText(StringUtil.capitalize(ReadOnlyStatusDialog.getTheseFilesMessage(conflicts))
                        + " " + (conflicts.size() > 1 ? "do" : "does")
                        + " not belong to the active changelist:");

    myFileList.setCellRenderer(new FileListRenderer());
    myFileList.setModel(new CollectionListModel<>(conflicts));

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
    mySecondsUntilOkEnabled = SECONDS_TO_LOCK_OK;

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
    //getRootPane().setDefaultButton(null);

  }

  @Override
  public void show() {
    // Prevent accidental confirmation of dialog when user presses Enter multiple times in quick succession
    startButtonCountdown(mySecondsUntilOkEnabled, myOKAction);
    super.show();
  }

  private void startButtonCountdown(int secondsDelay, final Action action) {
    if (secondsDelay == 0) {
      return;
    }
    final JRootPane rootPane = getRootPane();

    action.setEnabled(false);

    final JButton defaultButton;
    final JBLabel focusPlaceholder;
    if (rootPane.getDefaultButton() != null && rootPane.getDefaultButton().getAction() == action) {
      defaultButton = rootPane.getDefaultButton();
      Container parent = defaultButton.getParent();
      rootPane.setDefaultButton(null);


      // because we disabled the default button, it won't receive focus nor would any other component in the dialog
      // let's create an "invisible" component instead to transfer focus to while the button is disabled
      // and restore focus once the button is re-enabled and the focus is still on our placeholder
      focusPlaceholder = new JBLabel("");
      // It doesn't matter where we add the placeholder the order in which we add components matters, not the order within the container
      // To have the placeholder behave consistently with the button, DialogWrapper should support this feature
      parent.add(focusPlaceholder);
      // without pack, requestFocusInWindow doesn't work
      // would not be needed if DialogWrapper itself supported this feature, could be done with another custom property like DEFAULT_ACTION
      pack();
      focusPlaceholder.setFocusable(true);
      focusPlaceholder.requestFocusInWindow();
    }
    else {
      defaultButton = null;
      focusPlaceholder = null;
    }

    String originalText = defaultButton != null ? defaultButton.getText() : (String) action.getValue(Action.NAME);

    Timer timer = UIUtil.createNamedTimer(String.format("%s-button countdown: %ds", originalText, secondsDelay), 1000);
    timer.setInitialDelay(0);
    ActionListener actionListener = new ActionListener() {
      private int secondsRemaining = secondsDelay;

      @Override
      public void actionPerformed(ActionEvent e) {
        if (secondsRemaining == 0) {
          myOKAction.putValue(Action.NAME, originalText);
          myOKAction.setEnabled(true);
          if (defaultButton != null) {
            rootPane.setDefaultButton(defaultButton);
          }
          if (focusPlaceholder != null) {
            // Transfer focus back to the actual button
            if (focusPlaceholder.hasFocus()) {
              defaultButton.requestFocusInWindow();
              // don't need the placeholder anymore
              focusPlaceholder.setFocusable(false);
            }
          }
          timer.stop();
        }
        else {
          myOKAction.putValue(Action.NAME, originalText + "(" + secondsRemaining + ")");
        }

        secondsRemaining--;
      }
    };
    timer.addActionListener(actionListener);
    timer.start();
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

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    return new Action[] { new AbstractAction("&Configure...") {
      @Override
      public void actionPerformed(ActionEvent e) {
        ChangeListManagerImpl manager = ChangeListManagerImpl.getInstanceImpl(myProject);
        ShowSettingsUtil.getInstance().editConfigurable(myPanel, new ChangelistConflictConfigurable(manager));
      }
    }};
  }

  @Override
  protected String getHelpId() {
    return "project.propVCSSupport.ChangelistConflict";
  }
}
