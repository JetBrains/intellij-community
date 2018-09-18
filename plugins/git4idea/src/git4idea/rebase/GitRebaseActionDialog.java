// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
