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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.HashSet;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestPanel {
  private JTextField myTitleTextField;
  private JTextArea myDescriptionTextArea;
  private JComboBox myBranchComboBox;
  private JPanel myPanel;
  private AsyncProcessIcon myAsyncProcessIcon;

  public GithubCreatePullRequestPanel() {
    myDescriptionTextArea.setBorder(BorderFactory.createEtchedBorder());
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

  public void setBranch(@NotNull String branch) {
    if (StringUtil.isEmptyOrSpaces(branch)) {
      return;
    }
    for (int i = 0; i < myBranchComboBox.getItemCount(); i++) {
      Object element = myBranchComboBox.getItemAt(i);
      if (branch.equals(element)) {
        myBranchComboBox.setSelectedItem(element);
        return;
      }
    }

    myBranchComboBox.addItem(branch);
    myBranchComboBox.setSelectedItem(branch);
  }

  public void addBranches(@NotNull Collection<String> branches) {
    HashSet<Object> set = new HashSet<Object>(branches);
    for (int i = 0; i < myBranchComboBox.getItemCount(); i++) {
      set.remove(myBranchComboBox.getItemAt(i));
    }
    for (Object element : set) {
      myBranchComboBox.addItem(element);
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  @NotNull
  public JComponent getPreferredComponent() {
    return myTitleTextField;
  }

  public void setBusy(boolean busy) {
    if (busy) {
      myAsyncProcessIcon.resume();
    }
    else {
      myAsyncProcessIcon.suspend();
    }
  }

  public JComboBox getComboBox() {
    return myBranchComboBox;
  }

  public JComponent getTitleTextField() {
    return myTitleTextField;
  }

  public void setTitle(String title) {
    myTitleTextField.setText(title);
  }

  private void createUIComponents() {
    myAsyncProcessIcon = new AsyncProcessIcon("Loading available branches");
  }
}
