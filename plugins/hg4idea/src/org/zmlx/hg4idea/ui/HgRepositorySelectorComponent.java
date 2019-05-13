// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.ui;

import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.Collection;

public class HgRepositorySelectorComponent {
  private JComboBox repositorySelector;
  private JPanel mainPanel;

  public void setRoots(Collection<HgRepository> roots) {
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    for (HgRepository repo : roots) {
      model.addElement(repo);
    }
    repositorySelector.setModel(model);
    mainPanel.setVisible(roots.size() > 1);
  }

  public void setSelectedRoot(@Nullable HgRepository repository) {
    if (repository != null) {
      repositorySelector.setSelectedItem(repository);
    }
  }

  public void addActionListener(@NotNull ActionListener actionListener) {
    repositorySelector.addActionListener(actionListener);
  }

  public void setTitle(@NotNull String title) {
    mainPanel.setBorder(IdeBorderFactory.createTitledBorder(title, true));
  }

  @NotNull
  public HgRepository getRepository() {
    return (HgRepository)repositorySelector.getSelectedItem();
  }
}
