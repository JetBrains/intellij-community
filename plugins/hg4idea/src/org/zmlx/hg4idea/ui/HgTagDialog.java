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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgBranchReferenceValidator;
import org.zmlx.hg4idea.util.HgReferenceValidator;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.Collection;

public class HgTagDialog extends DialogWrapper {

  private JPanel contentPanel;
  private JTextField tagTxt;
  private HgRepositorySelectorComponent hgRepositorySelectorComponent;

  public HgTagDialog(@NotNull Project project, @NotNull Collection<HgRepository> repositories, @Nullable HgRepository selectedRepo) {
    super(project, false);
    hgRepositorySelectorComponent.setTitle("Select repository to tag");
    DocumentListener documentListener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validateFields();
      }
    };

    tagTxt.getDocument().addDocumentListener(documentListener);

    setTitle("Tag");
    init();

    setRoots(repositories, selectedRepo);
  }

  public String getTagName() {
    return tagTxt.getText();
  }

  public VirtualFile getRepository() {
    return hgRepositorySelectorComponent.getRepository().getRoot();
  }

  private void setRoots(@NotNull Collection<HgRepository> repositories, @Nullable HgRepository selectedRepo) {
    hgRepositorySelectorComponent.setRoots(repositories);
    hgRepositorySelectorComponent.setSelectedRoot(selectedRepo);
  }

  protected JComponent createCenterPanel() {
    return contentPanel;
  }

  private void validateFields() {
    HgReferenceValidator validator = new HgBranchReferenceValidator(hgRepositorySelectorComponent.getRepository());
    String name = getTagName();
    if (!validator.checkInput(name)) {
      String message = validator.getErrorText(name);
      setErrorText(message == null ? "You have to specify tag name." : message);
      setOKActionEnabled(false);
      return;
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return tagTxt;
  }
}
