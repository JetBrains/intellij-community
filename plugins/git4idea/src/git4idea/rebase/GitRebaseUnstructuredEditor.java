/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.config.GitConfigUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * The dialog used for the unstructured information from git rebase,
 * usually the commit message after choosing reword or squash interactive rebase actions.
 */
public class GitRebaseUnstructuredEditor extends DialogWrapper {
  private JTextArea myTextArea;
  private JPanel myPanel;
  private JLabel myGitRootLabel;
  private final String myEncoding;
  private final File myFile;

  protected GitRebaseUnstructuredEditor(@NotNull Project project, @NotNull VirtualFile root, @NotNull String rebaseFilePath)
    throws IOException {
    super(project, true);
    setTitle(GitBundle.message("rebase.unstructured.editor.title"));
    setOKButtonText(GitBundle.message("rebase.unstructured.editor.button"));
    myGitRootLabel.setText(root.getPresentableUrl());
    myEncoding = GitConfigUtil.getCommitEncoding(project, root);
    myFile = new File(rebaseFilePath);
    myTextArea.setText(FileUtil.loadFile(myFile, myEncoding));
    myTextArea.setCaretPosition(0);
    init();
  }

  /**
   * Save content to the file
   */
  public void save() throws IOException {
    FileUtil.writeToFile(myFile, myTextArea.getText().getBytes(myEncoding));
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTextArea;
  }
}
