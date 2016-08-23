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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubFullPath;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.Collection;

import static org.jetbrains.plugins.github.GithubCreatePullRequestWorker.BranchInfo;
import static org.jetbrains.plugins.github.GithubCreatePullRequestWorker.ForkInfo;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestPanel {
  private JTextField myTitleTextField;
  private JTextArea myDescriptionTextArea;
  private ComboBox myBranchComboBox;
  private SortedComboBoxModel<ForkInfo> myForkModel;
  private SortedComboBoxModel<BranchInfo> myBranchModel;
  private JPanel myPanel;
  private JButton myShowDiffButton;
  private JButton mySelectForkButton;
  private JLabel myForkLabel;
  private ComboBox myForkComboBox;

  private boolean myTitleDescriptionUserModified = false;

  public GithubCreatePullRequestPanel() {
    myDescriptionTextArea.setBorder(BorderFactory.createEtchedBorder());

    myBranchModel = new SortedComboBoxModel<>((o1, o2) -> StringUtil.naturalCompare(o1.getRemoteName(), o2.getRemoteName()));
    myBranchComboBox.setModel(myBranchModel);

    myForkModel = new SortedComboBoxModel<>((o1, o2) -> StringUtil.naturalCompare(o1.getPath().getUser(), o2.getPath().getUser()));
    myForkComboBox.setModel(myForkModel);

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

  @Nullable
  public ForkInfo getSelectedFork() {
    return myForkModel.getSelectedItem();
  }

  @Nullable
  public BranchInfo getSelectedBranch() {
    return myBranchModel.getSelectedItem();
  }

  public void setSelectedFork(@Nullable GithubFullPath path) {
    if (path != null) {
      for (ForkInfo info : myForkModel.getItems()) {
        if (path.equals(info.getPath())) {
          myForkModel.setSelectedItem(info);
          return;
        }
      }
    }

    if (myForkModel.getSize() > 0) myForkModel.setSelectedItem(myForkModel.get(0));
  }

  public void setSelectedBranch(@Nullable String branch) {
    if (branch != null) {
      for (BranchInfo info : myBranchModel.getItems()) {
        if (branch.equals(info.getRemoteName())) {
          myBranchModel.setSelectedItem(info);
          return;
        }
      }
    }

    if (myBranchModel.getSize() > 0) myBranchModel.setSelectedItem(myBranchModel.get(0));
  }

  public void setForks(@NotNull Collection<ForkInfo> forks) {
    myForkModel.setSelectedItem(null);
    myForkModel.setAll(forks);
  }

  public void setBranches(@NotNull Collection<BranchInfo> branches) {
    myBranchModel.setSelectedItem(null);
    myBranchModel.setAll(branches);
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

  public void setDiffEnabled(boolean enabled) {
    myShowDiffButton.setEnabled(enabled);
  }

  @NotNull
  public JComponent getTitleTextField() {
    return myTitleTextField;
  }

  @NotNull
  public JButton getSelectForkButton() {
    return mySelectForkButton;
  }

  @NotNull
  public JButton getShowDiffButton() {
    return myShowDiffButton;
  }

  @NotNull
  public ComboBox getForkComboBox() {
    return myForkComboBox;
  }

  @NotNull
  public ComboBox getBranchComboBox() {
    return myBranchComboBox;
  }

  public JPanel getPanel() {
    return myPanel;
  }

  @NotNull
  public JComponent getPreferredComponent() {
    return myTitleTextField;
  }
}
