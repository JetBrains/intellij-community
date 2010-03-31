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
import git4idea.i18n.GitBundle;

import javax.swing.*;

/**
 * Update options
 */
public class GitUpdateOptionsPanel {
  /**
   * The merge policy
   */
  private static final String MERGE = GitBundle.getString("update.options.type.merge");
  /**
   * The rebase policy
   */
  private static final String REBASE = GitBundle.getString("update.options.type.rebase");
  /**
   * The default branch policy
   */
  public static final String DEFAULT = GitBundle.getString("update.options.type.default");
  /**
   * The type combobox
   */
  private JComboBox myTypeComboBox;
  /**
   * The root panel
   */
  private JPanel myPanel;
  /**
   * The combobox that specifies save on update policy
   */
  private JComboBox myAutoSaveFilesOnComboBox;

  /**
   * A constructor
   */
  public GitUpdateOptionsPanel() {
    myTypeComboBox.addItem(DEFAULT);
    myTypeComboBox.addItem(REBASE);
    myTypeComboBox.addItem(MERGE);
    myAutoSaveFilesOnComboBox.addItem(UpdatePolicyUtils.SAVE_STASH);
    myAutoSaveFilesOnComboBox.addItem(UpdatePolicyUtils.SAVE_SHELVE);
    myAutoSaveFilesOnComboBox.addItem(UpdatePolicyUtils.SAVE_KEEP);
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
    return type != settings.UPDATE_TYPE || UpdatePolicyUtils.getUpdatePolicy(myAutoSaveFilesOnComboBox) != settings.updateChangesPolicy();
  }

  /**
   * @return get the currently selected update type
   */
  private UpdateType getUpdateType() {
    UpdateType type = null;
    String typeVal = (String)myTypeComboBox.getSelectedItem();
    if (REBASE.equals(typeVal)) {
      type = UpdateType.REBASE;
    }
    else if (MERGE.equals(typeVal)) {
      type = UpdateType.MERGE;
    }
    else if (DEFAULT.equals(typeVal)) {
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
    settings.UPDATE_TYPE = getUpdateType();
    settings.UPDATE_CHANGES_POLICY = UpdatePolicyUtils.getUpdatePolicy(myAutoSaveFilesOnComboBox);
  }

  /**
   * Update panel according to settings
   *
   * @param settings the settings to use
   */
  public void updateFrom(GitVcsSettings settings) {
    String value = null;
    switch (settings.UPDATE_TYPE) {
      case REBASE:
        value = REBASE;
        break;
      case MERGE:
        value = MERGE;
        break;
      case BRANCH_DEFAULT:
        value = DEFAULT;
        break;
      default:
        assert false : "Unknown value of update type: " + settings.UPDATE_TYPE;
    }
    myTypeComboBox.setSelectedItem(value);
    UpdatePolicyUtils.updatePolicyItem(settings.updateChangesPolicy(), myAutoSaveFilesOnComboBox);
  }

}
