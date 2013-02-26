package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.GithubSettings;
import org.jetbrains.plugins.github.GithubUtil;

import javax.swing.*;
import java.io.IOException;

/**
 * @author oleg
 * @date 10/20/10
 */
public class GithubLoginDialog extends DialogWrapper {

  private static Logger LOG  = GithubUtil.LOG;

  private final GithubLoginPanel myGithubLoginPanel;
  private final Project myProject;

  // TODO: login to github must be merged with tasks server settings
  public GithubLoginDialog(final Project project) {
    super(project, true);
    myProject = project;
    myGithubLoginPanel = new GithubLoginPanel(this);
    final GithubSettings settings = GithubSettings.getInstance();
    myGithubLoginPanel.setHost(settings.getHost());
    myGithubLoginPanel.setLogin(settings.getLogin());
    myGithubLoginPanel.setPassword(settings.getPassword());
    setTitle("Login to GitHub");
    setOKButtonText("Login");
    init();
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[] {getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    return myGithubLoginPanel.getPanel();
  }

  @Override
  protected String getHelpId() {
    return "login_to_github";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGithubLoginPanel.getPreferrableFocusComponent();
  }

  @Override
  protected void doOKAction() {
    final String login = myGithubLoginPanel.getLogin();
    final String password = myGithubLoginPanel.getPassword();
    final String host = myGithubLoginPanel.getHost();
    try {
      boolean loggedSuccessfully = GithubUtil.checkCredentials(myProject, host, login, password);
      if (loggedSuccessfully) {
        final GithubSettings settings = GithubSettings.getInstance();
        settings.setLogin(login);
        settings.setPassword(password);
        settings.setHost(host);
        super.doOKAction();
      }
      else {
        setErrorText("Can't login with given credentials");
      }
    }
    catch (IOException e) {
      LOG.info(e);
      setErrorText("Can't login: " + GithubUtil.getErrorTextFromException(e));
    }
  }

  public void clearErrors() {
    setErrorText(null);
  }
}