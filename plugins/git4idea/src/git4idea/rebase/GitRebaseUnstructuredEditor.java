package git4idea.rebase;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.config.GitConfigUtil;
import git4idea.i18n.GitBundle;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * The dialog used for the unstructured information from git rebase.
 */
public class GitRebaseUnstructuredEditor extends DialogWrapper {
  /**
   * The text with information from the GIT
   */
  private JTextArea myTextArea;
  /**
   * The root panel of the dialog
   */
  private JPanel myPanel;
  /**
   * The label that contains the git root path
   */
  private JLabel myGitRootLabel;
  /**
   * The file encoding
   */
  private final String encoding;
  /**
   * The file being edited
   */
  private final File myFile;

  /**
   * The constructor
   *
   * @param project the context project
   * @param root    the Git root
   * @param path    the path to edit
   * @throws IOException if there is an IO problem
   */
  protected GitRebaseUnstructuredEditor(Project project, VirtualFile root, String path) throws IOException {
    super(project, true);
    setTitle(GitBundle.message("rebase.unstructured.editor.title"));
    setOKButtonText(GitBundle.message("rebase.unstructured.editor.button"));
    myGitRootLabel.setText(root.getPresentableUrl());
    encoding = GitConfigUtil.getCommitEncoding(project, root);
    myFile = new File(path);
    myTextArea.setText(new String(FileUtil.loadFileText(myFile, encoding)));
    init();
  }

  /**
   * Save content to the file
   *
   * @throws IOException if there is an IO problem
   */
  public void save() throws IOException {
    FileUtil.writeToFile(myFile, myTextArea.getText().getBytes(encoding));
  }

  /**
   * {@inheritDoc}
   */
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }
}
