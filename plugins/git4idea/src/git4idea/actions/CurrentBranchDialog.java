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
package git4idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitUIUtil;

import javax.swing.*;
import java.util.List;

/**
 * Current branch dialog
 */
public class CurrentBranchDialog extends DialogWrapper {
  /**
   * The container panel
   */
  private JPanel myPanel;
  /**
   * Git root selector
   */
  private JComboBox myGitRoot;
  /**
   * The current branch
   */
  private JLabel myCurrentBranch;

  /**
   * A constructor
   *
   * @param project     the context project
   * @param roots       the git roots for the project
   * @param defaultRoot the default root
   */
  protected CurrentBranchDialog(Project project, List<VirtualFile> roots, VirtualFile defaultRoot) {
    super(project, true);
    setTitle(GitBundle.getString("current.branch.title"));
    GitUIUtil.setupRootChooser(project, roots, defaultRoot, myGitRoot, myCurrentBranch);
    init();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  /**
   * {@inheritDoc}
   */
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
