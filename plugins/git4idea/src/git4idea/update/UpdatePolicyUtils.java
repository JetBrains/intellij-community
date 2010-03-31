/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import git4idea.i18n.GitBundle;

import javax.swing.*;

/**
 * The utilities for update policy
 */
public class UpdatePolicyUtils {
  /**
   * The auto-save policy
   */
  public static final String SAVE_KEEP = GitBundle.getString("update.options.save.keep");
  /**
   * The auto-save policy
   */
  public static final String SAVE_SHELVE = GitBundle.getString("update.options.save.shelve");
  /**
   * The auto-save policy
   */
  public static final String SAVE_STASH = GitBundle.getString("update.options.save.stash");

  /**
   * The private constructor
   */
  private UpdatePolicyUtils() {
  }

  /**
   * Get currently selected update policy
   *
   * @param autoSaveFilesOnComboBox the combo box to check
   * @return the currently selected update policy
   */
  public static GitVcsSettings.UpdateChangesPolicy getUpdatePolicy(final JComboBox autoSaveFilesOnComboBox) {
    GitVcsSettings.UpdateChangesPolicy p;
    Object i = autoSaveFilesOnComboBox.getSelectedItem();
    if (SAVE_KEEP.equals(i)) {
      p = GitVcsSettings.UpdateChangesPolicy.KEEP;
    }
    else if (SAVE_STASH.equals(i)) {
      p = GitVcsSettings.UpdateChangesPolicy.STASH;
    }
    else if (SAVE_SHELVE.equals(i)) {
      p = GitVcsSettings.UpdateChangesPolicy.SHELVE;
    }
    else {
      throw new IllegalStateException("Unknown auto-save policy");
    }
    return p;
  }

  /**
   * Select a correct item in update changes policy combobox
   *
   * @param updateChangesPolicy the policy value
   * @param policyComboBox      the combobox to update
   */
  public static void updatePolicyItem(GitVcsSettings.UpdateChangesPolicy updateChangesPolicy, final JComboBox policyComboBox) {
    String value = null;
    switch (updateChangesPolicy) {
      case KEEP:
        value = SAVE_KEEP;
        break;
      case SHELVE:
        value = SAVE_SHELVE;
        break;
      case STASH:
        value = SAVE_STASH;
        break;
      default:
        assert false : "Unknown value of update type: " + updateChangesPolicy;
    }
    policyComboBox.setSelectedItem(value);
  }
}
