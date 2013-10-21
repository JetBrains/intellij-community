/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.Collection;
import java.util.Comparator;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestPanel {
  private JTextField myTitleTextField;
  private JTextArea myDescriptionTextArea;
  private ComboBox myBranchComboBox;
  private SortedComboBoxModel<String> myBranchModel;
  private JPanel myPanel;
  private JButton myShowDiffButton;
  private JButton mySelectForkButton;
  private JLabel myForkLabel;
  private AsyncProcessIcon myBusyIcon;

  private boolean myTitleDescriptionUserModified = false;

  public GithubCreatePullRequestPanel() {
    myDescriptionTextArea.setBorder(BorderFactory.createEtchedBorder());
    myBranchModel = new SortedComboBoxModel<String>(new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        return StringUtil.naturalCompare(o1, o2);
      }
    });
    myBranchComboBox.setModel(myBranchModel);

    DocumentListener userModifiedDocumentListener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myTitleDescriptionUserModified = true;
      }
    };
    myTitleTextField.getDocument().addDocumentListener(userModifiedDocumentListener);
    myDescriptionTextArea.getDocument().addDocumentListener(userModifiedDocumentListener);
  }

  @NotNull
  public String getTitle() {
    return myTitleTextField.getText();
  }

  @NotNull
  public String getDescription() {
    return myDescriptionTextArea.getText();
  }

  @NotNull
  public String getBranch() {
    return myBranchComboBox.getSelectedItem().toString();
  }

  public void setDiffEnabled(boolean enabled) {
    myShowDiffButton.setEnabled(enabled);
  }

  public void setSelectedBranch(@Nullable String branch) {
    if (StringUtil.isEmptyOrSpaces(branch)) {
      return;
    }

    myBranchComboBox.setSelectedItem(branch);
  }

  public void setBranches(@NotNull Collection<String> branches) {
    myBranchModel.clear();
    myBranchModel.addAll(branches);
    if (branches.size() > 0) {
      myBranchComboBox.setSelectedIndex(0);
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  @NotNull
  public JComponent getPreferredComponent() {
    return myTitleTextField;
  }

  @NotNull
  public JComponent getBranchEditor() {
    return myBranchComboBox;
  }

  @NotNull
  public JComponent getTitleTextField() {
    return myTitleTextField;
  }

  @NotNull
  public JButton getShowDiffButton() {
    return myShowDiffButton;
  }

  @NotNull
  public JButton getSelectForkButton() {
    return mySelectForkButton;
  }

  @NotNull
  public ComboBox getBranchComboBox() {
    return myBranchComboBox;
  }

  public void setTitle(@Nullable String title) {
    myTitleTextField.setText(title);
    myTitleDescriptionUserModified = false;
  }

  public void setDescription(@Nullable String title) {
    myDescriptionTextArea.setText(title);
    myTitleDescriptionUserModified = false;
  }

  public boolean isTitleDescriptionEmptyOrNotModified() {
    return !myTitleDescriptionUserModified ||
           (StringUtil.isEmptyOrSpaces(myTitleTextField.getText()) && StringUtil.isEmptyOrSpaces(myDescriptionTextArea.getText()));
  }

  public void setForkName(@NotNull String forkName) {
    myForkLabel.setText(forkName);
  }

  public void setBusy(boolean enabled) {
    if (enabled) {
      myBusyIcon.resume();
    }
    else {
      myBusyIcon.suspend();
    }
  }

  private void createUIComponents() {
    myBusyIcon = new AsyncProcessIcon("Loading diff...");
    myBusyIcon.suspend();
  }
}
