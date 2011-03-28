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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * The utilities for update policy
 */
public class UpdatePolicyUtils {
  /**
   * The private constructor
   */
  private UpdatePolicyUtils() {
  }

  /**
   * Set policy value to radio buttons
   *
   * @param updateChangesPolicy the policy value to set
   * @param stashRadioButton    the stash radio button
   * @param shelveRadioButton   the shelve radio button
   * @param keepRadioButton     the keep radio button
   */
  public static void updatePolicyItem(GitVcsSettings.UpdateChangesPolicy updateChangesPolicy,
                                      JRadioButton stashRadioButton,
                                      JRadioButton shelveRadioButton) {
    switch (updateChangesPolicy == null ? GitVcsSettings.UpdateChangesPolicy.STASH : updateChangesPolicy) {
      case STASH:
        stashRadioButton.setSelected(true);
        return;
      case SHELVE:
        shelveRadioButton.setSelected(true);
        return;
      default:
        assert false : "Unknown policy value: " + updateChangesPolicy;
    }
  }

  /**
   * Get policy value from radio buttons
   *
   * @param stashRadioButton  the stash radio button
   * @param shelveRadioButton the shelve radio button
   * @param keepRadioButton   the keep radio button
   * @return the policy value
   */
  public static GitVcsSettings.UpdateChangesPolicy getUpdatePolicy(@NotNull JRadioButton stashRadioButton,
                                                                   @NotNull JRadioButton shelveRadioButton) {

    if (stashRadioButton.isSelected()) {
      return GitVcsSettings.UpdateChangesPolicy.STASH;
    } else if (shelveRadioButton.isSelected()) {
      return GitVcsSettings.UpdateChangesPolicy.SHELVE;
    } else {
      // the stash is a default policy, in case if the policy could not be determined
      return GitVcsSettings.UpdateChangesPolicy.STASH;
    }
  }
}
