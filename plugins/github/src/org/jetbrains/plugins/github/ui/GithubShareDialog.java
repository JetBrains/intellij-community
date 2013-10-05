package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author oleg
 * @date 10/22/10
 */
public class GithubShareDialog extends DialogWrapper {
  private static final Pattern GITHUB_REPO_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]+");
  private final GithubSharePanel myGithubSharePanel;
  private final Set<String> myAvailableNames;

  public GithubShareDialog(final Project project, final Set<String> availableNames, final boolean privateRepoAllowed) {
    super(project);
    myAvailableNames = availableNames;
    myGithubSharePanel = new GithubSharePanel(this);
    init();
    setTitle("Share Project On GitHub");
    setOKButtonText("Share");
    myGithubSharePanel.setRepositoryName(project.getName());
    myGithubSharePanel.setPrivateRepoAvailable(privateRepoAllowed);
    init();
    updateOkButton();
  }

  @Override
  protected String getHelpId() {
    return "github.share";
  }

  @Override
  protected String getDimensionServiceKey() {
    return "Github.ShareDialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    return myGithubSharePanel.getPanel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGithubSharePanel.getPreferredFocusComponent();
  }

  public void updateOkButton() {
    final String repositoryName = getRepositoryName();
    if (StringUtil.isEmpty(repositoryName)){
      setErrorText("No repository name selected");
      setOKActionEnabled(false);
      return;
    }
    if (myAvailableNames.contains(repositoryName)){
      setErrorText("Repository with selected name already exists");
      setOKActionEnabled(false);
      return;
    }
    if (!GITHUB_REPO_PATTERN.matcher(repositoryName).matches()){
      setErrorText("Invalid repository name. Name should consist of letters, numbers, dashes, dots and underscores");
      setOKActionEnabled(false);
      return;
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  public String getRepositoryName() {
    return myGithubSharePanel.getRepositoryName();
  }

  public boolean isPrivate() {
    return myGithubSharePanel.isPrivate();
  }

  public String getDescription() {
    return myGithubSharePanel.getDescription();
  }

  @TestOnly
  public void testSetRepositoryName(@NotNull String name) {
    myGithubSharePanel.setRepositoryName(name);
  }
}
