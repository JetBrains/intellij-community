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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.Collection;

public class HgRepositorySelectorComponent {
  private JComboBox repositorySelector;
  private JPanel mainPanel;

  public void setRoots(Collection<VirtualFile> roots) {
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    for (VirtualFile repo : roots) {
      model.addElement(new RepositoryDisplay(repo));
    }
    repositorySelector.setModel(model);
    mainPanel.setVisible(roots.size() > 1);
  }

  public void setSelectedRoot(@Nullable VirtualFile repository) {
    if (repository != null) {
      repositorySelector.setSelectedItem(new RepositoryDisplay(repository));
    }
  }

  public void addActionListener(ActionListener actionListener) {
    repositorySelector.addActionListener(actionListener);
  }

  public void setTitle(String title) {
    mainPanel.setBorder(IdeBorderFactory.createTitledBorder(title, true));
  }

  public VirtualFile getRepository() {
    return ((RepositoryDisplay) repositorySelector.getSelectedItem()).repo;
  }

  private class RepositoryDisplay {
    @NotNull private final VirtualFile repo;

    public RepositoryDisplay(@NotNull VirtualFile repo) {
      this.repo = repo;
    }

    @Override
    public String toString() {
      return repo.getPresentableUrl();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof RepositoryDisplay && this.repo.equals(((RepositoryDisplay)obj).repo);
    }

    @Override
    public int hashCode() {
      return repo.hashCode();
    }
  }

}
