package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.plugins.github.RepositoryInfo;
import org.jetbrains.plugins.github.UnknownRepositoryInfo;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 */
public class GithubCloneProjectDialog extends DialogWrapper {

  private static final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile("[\\w\\d-_\\.]+/[\\w\\d-_\\.]+");
  private GithubCloneProjectPane myGithubCloneProjectPane;
  private HashMap<String, RepositoryInfo> myRepositoryInfoHashMap;

  public GithubCloneProjectDialog(final Project project, final List<RepositoryInfo> repos) {
    super(project, true);
    myGithubCloneProjectPane = new GithubCloneProjectPane(this);
    setTitle("Select repository to clone");
    setOKButtonText("Clone");
    myRepositoryInfoHashMap = new HashMap<String, RepositoryInfo>();
    for (RepositoryInfo repo : repos) {
      myRepositoryInfoHashMap.put(repo.getId(), repo);
    }
    final ArrayList<String> ids = new ArrayList<String>(myRepositoryInfoHashMap.keySet());
    myGithubCloneProjectPane.setAvailableRepos(ids);
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
    final String selectedRepositoryId = getSelectedRepositoryId();
    if (!PATTERN.matcher(selectedRepositoryId).matches()){
      setErrorText("Wrong repository format. owner/repository expected");
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
    final String id = getSelectedRepositoryId();
    return myRepositoryInfoHashMap.containsKey(id) ? myRepositoryInfoHashMap.get(id) : new UnknownRepositoryInfo(id);
  }

  private String getSelectedRepositoryId() {
    return myGithubCloneProjectPane.getSelectedRepository();
  }

  public String getSelectedPath() {
    return myGithubCloneProjectPane.getSelectedPath();
  }

  public void setSelectedPath(final String path) {
    myGithubCloneProjectPane.setSelectedPath(path);
  }
}