/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.rebase;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * The rebase action dialog
 */
public class GitRebaseActionDialog extends DialogWrapper {
  /**
   * The root selector
   */
  private ComboBox myGitRootComboBox;
  /**
   * The root panel
   */
  private JPanel myPanel;

  /**
   * A constructor
   *
   * @param project     the project to select
   * @param title       the dialog title
   * @param roots       the git repository roots for the project
   * @param defaultRoot the guessed default root
   */
  public GitRebaseActionDialog(Project project, String title, List<VirtualFile> roots, VirtualFile defaultRoot) {
    super(project, true);
    GitUIUtil.setupRootChooser(project, roots, defaultRoot, myGitRootComboBox, null);
    setTitle(title);
    setOKButtonText(title);
    init();
  }


  /**
   * Show dialog and select root
   *
   * @return selected root or null if the dialog has been cancelled
   */
  @Nullable
  public VirtualFile selectRoot() {
    return isOK() ? (VirtualFile)myGitRootComboBox.getSelectedItem() : null;
  }


  /**
   * {@inheritDoc}
   */
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
