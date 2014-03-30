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
import com.intellij.ui.SortedComboBoxModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Comparator;

/**
 * @author Aleksey Pivovarov
 */
public class GithubSelectForkPanel {
  private final SortedComboBoxModel<String> myModel;
  private JPanel myPanel;
  private ComboBox myComboBox;

  public GithubSelectForkPanel() {
    myModel = new SortedComboBoxModel<String>(new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        return StringUtil.naturalCompare(o1, o2);
      }
    });

    myComboBox.setModel(myModel);
  }

  public void setUsers(@NotNull Collection<String> users) {
    myModel.clear();
    myModel.addAll(users);
    if (users.size() > 0) {
      myComboBox.setSelectedIndex(0);
    }
  }

  @NotNull
  public String getUser() {
    return myComboBox.getSelectedItem().toString();
  }

  public void setSelectedUser(@Nullable String user) {
    if (StringUtil.isEmptyOrSpaces(user)) {
      return;
    }

    myComboBox.setSelectedItem(user);
  }

  public JPanel getPanel() {
    return myPanel;
  }
}
