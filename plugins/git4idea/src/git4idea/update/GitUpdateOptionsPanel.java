/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import git4idea.config.GitVcsSettings;
import git4idea.config.GitVcsSettings.UpdateType;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Update options panel
 */
public class GitUpdateOptionsPanel {
  /**
   * The root panel
   */
  private JPanel myPanel;
  /**
   * Update strategy option
   */
  private JRadioButton myBranchDefaultRadioButton;
  /**
   * Update strategy option
   */
  private JRadioButton myForceRebaseRadioButton;
  /**
   * Update strategy option
   */
  private JRadioButton myForceMergeRadioButton;
  /**
   * Save files option option
   */
  private JRadioButton myStashRadioButton;
  /**
   * Save files option option
   */
  private JRadioButton myShelveRadioButton;
  /**
   * Save files option option
   */
  private JRadioButton myKeepRadioButton;

  /**
   * The constructor
   */
  public GitUpdateOptionsPanel() {
    myForceRebaseRadioButton.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        boolean keepPossible = !myForceRebaseRadioButton.isSelected();
        if (!keepPossible && myKeepRadioButton.isSelected()) {
          myStashRadioButton.setSelected(true);
        }
        myKeepRadioButton.setEnabled(keepPossible);
      }
    });
  }

  /**
   * @return the panel component
   */
  public JComponent getPanel() {
    return myPanel;
  }

  /**
   * Check if the panel is modified relatively to settings
   *
   * @param settings the settings to compare to
   * @return true if the UI modified the settings
   */
  public boolean isModified(GitVcsSettings settings) {
    UpdateType type = getUpdateType();
    return type != settings.getUpdateType() || updateSaveFilesPolicy() != settings.updateChangesPolicy();
  }

  /**
   * @return get policy value from selected radio buttons
   */
  private GitVcsSettings.UpdateChangesPolicy updateSaveFilesPolicy() {
    return UpdatePolicyUtils.getUpdatePolicy(myStashRadioButton, myShelveRadioButton, myKeepRadioButton);
  }

  /**
   * @return get the currently selected update type
   */
  private UpdateType getUpdateType() {
    UpdateType type = null;
    if (myForceRebaseRadioButton.isSelected()) {
      type = UpdateType.REBASE;
    }
    else if (myForceMergeRadioButton.isSelected()) {
      type = UpdateType.MERGE;
    }
    else if (myBranchDefaultRadioButton.isSelected()) {
      type = UpdateType.BRANCH_DEFAULT;
    }
    assert type != null;
    return type;
  }

  /**
   * Save configuration to settings object
   *
   * @param settings the settings to save to
   */
  public void applyTo(GitVcsSettings settings) {
    settings.setUpdateType(getUpdateType());
    settings.setUpdateChangesPolicy(updateSaveFilesPolicy());
  }

  /**
   * Update panel according to settings
   *
   * @param settings the settings to use
   */
  public void updateFrom(GitVcsSettings settings) {
    switch (settings.getUpdateType()) {
      case REBASE:
        myForceRebaseRadioButton.setSelected(true);
        break;
      case MERGE:
        myForceMergeRadioButton.setSelected(true);
        break;
      case BRANCH_DEFAULT:
        myBranchDefaultRadioButton.setSelected(true);
        break;
      default:
        assert false : "Unknown value of update type: " + settings.getUpdateType();
    }
    UpdatePolicyUtils.updatePolicyItem(settings.updateChangesPolicy(), myStashRadioButton, myShelveRadioButton, myKeepRadioButton);
  }

}
