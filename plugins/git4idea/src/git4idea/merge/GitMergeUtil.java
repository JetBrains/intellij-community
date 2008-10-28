/*
 * Copyright 2000-2008 JetBrains s.r.o.
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
package git4idea.merge;

import com.intellij.ide.util.ElementsChooser;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Utilities for merge
 */
public class GitMergeUtil {
  /**
   * The item representing default strategy
   */
  public static final String DEFAULT_STRATEGY = GitBundle.getString("merge.default.strategy");

  /**
   * A private constructor for utility class
   */
  private GitMergeUtil() {
  }


  /**
   * Get a list of merge strategies for the specified branch cuont
   *
   * @param branchCount
   * @return an array of strategy names
   */
  @NonNls
  public static String[] getMergeStrategies(int branchCount) {
    if (branchCount < 0) {
      throw new IllegalArgumentException("Brach count must be non-negative: " + branchCount);
    }
    switch (branchCount) {
      case 0:
        return new String[]{DEFAULT_STRATEGY};
      case 1:
        return new String[]{DEFAULT_STRATEGY, "resolve", "recursive", "octopus", "ours", "subtree"};
      default:
        return new String[]{DEFAULT_STRATEGY, "octopus", "ours"};
    }
  }

  /**
   * Initialize no commit checkbox (for both merge and pull dialog)
   *
   * @param addLogInformationCheckBox a log information checkbox
   * @param commitMessage a commit message text field or null
   * @param noCommitCheckBox a no commit checkbox to configure
   */
  public static void setupNoCommitCheckbox(final JCheckBox addLogInformationCheckBox, final JTextField commitMessage,
                                            final JCheckBox noCommitCheckBox) {
    noCommitCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean selected = noCommitCheckBox.isSelected();
        if(commitMessage != null) {
          commitMessage.setEnabled(!selected);
        }
        if (selected) {
          addLogInformationCheckBox.setSelected(false);
        }
        addLogInformationCheckBox.setEnabled(!selected);
      }
    });
  }

  /**
   * Setup strategies dropdown. The set of strategies changes according to amount of selected elements in branchChooser.
   * 
   * @param branchChooser a branch chooser
   * @param noCommitCheckBox no commit checkbox
   * @param strategy a strategy selector
   */
  public static void setupStrategies(final ElementsChooser<String> branchChooser,
                                      final JCheckBox noCommitCheckBox, final JComboBox strategy) {
    final ElementsChooser.ElementsMarkListener<String> listener = new ElementsChooser.ElementsMarkListener<String>() {
      private void updateStrategies(final List<String> elements) {
        strategy.removeAllItems();
        for (String s : getMergeStrategies(elements.size())) {
          strategy.addItem(s);
        }
        strategy.setSelectedItem(DEFAULT_STRATEGY);
      }
      public void elementMarkChanged(final String element, final boolean isMarked) {
        final List<String> elements = branchChooser.getMarkedElements();
        if (elements.size() == 0) {
          strategy.setEnabled(false);
          updateStrategies(elements);
        }
        else if (elements.size() == 1) {
          strategy.setEnabled(true);
          updateStrategies(elements);
          noCommitCheckBox.setEnabled(true);
          noCommitCheckBox.setSelected(false);
        }
        else {
          strategy.setEnabled(true);
          updateStrategies(elements);
          noCommitCheckBox.setEnabled(false);
          noCommitCheckBox.setSelected(false);
        }
      }
    };
    listener.elementMarkChanged(null, true);
    branchChooser.addElementsMarkListener(listener);
  }
}
