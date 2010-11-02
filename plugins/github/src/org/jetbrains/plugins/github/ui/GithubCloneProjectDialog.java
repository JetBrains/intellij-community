package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.github.RepositoryInfo;

import javax.swing.*;
import java.util.List;

/**
 * @author oleg
 */
public class GithubCloneProjectDialog extends DialogWrapper {

  private GithubCloneProjectPane myGithubCloneProjectPane;

  public GithubCloneProjectDialog(final Project project, final List<RepositoryInfo> repos) {
    super(project, true);
    myGithubCloneProjectPane = new GithubCloneProjectPane(this);
    setTitle("Select repository to clone");
    setOKButtonText("Clone");
    myGithubCloneProjectPane.setAvailableRepos(repos);
    init();
    setOKActionEnabled(false);
  }

  protected Action[] createActions() {
    return new Action[] {getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    return myGithubCloneProjectPane.getPanel();
  }

  @Override
  protected String getHelpId() {
    return "github";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGithubCloneProjectPane.getPreferrableFocusComponent();
  }

  public void updateOkButton() {
    if (getSelectedRepository() == null){
      setErrorText("No repository selected");
      setOKActionEnabled(false);
      return;
    }
    final String path = getSelectedPath();
    if (path == null) {
      setErrorText("Please specify selected folder");
      setOKActionEnabled(false);
      return;
    }
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null || !file.exists() || !file.isDirectory()){
      setErrorText("Cannot find selected folder");
      setOKActionEnabled(false);
      return;
    }
    final String projectName = getProjectName();
    if (file.findChild(projectName) != null){
      setErrorText("Folder " + projectName + " already exists");
      setOKActionEnabled(false);
      return;
    }

    setErrorText(null);
    setOKActionEnabled(true);
  }

  public String getProjectName() {
    return myGithubCloneProjectPane.getProjectName();
  }

  public RepositoryInfo getSelectedRepository() {
    return myGithubCloneProjectPane.getSelectedRepository();
  }

  public String getSelectedPath() {
    return myGithubCloneProjectPane.getSelectedPath();
  }

  public void setSelectedPath(final String path) {
    myGithubCloneProjectPane.setSelectedPath(path);
  }
}