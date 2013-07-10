package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.GithubAuthData;
import org.jetbrains.plugins.github.GithubSettings;
import org.jetbrains.plugins.github.GithubUtil;

import javax.swing.*;
import java.io.IOException;

/**
 * @author oleg
 * @date 10/20/10
 */
public class GithubLoginDialog extends DialogWrapper {

  private static final Logger LOG = GithubUtil.LOG;

  private final GithubLoginPanel myGithubLoginPanel;

  public GithubLoginDialog(final Project project) {
    super(project, true);
    myGithubLoginPanel = new GithubLoginPanel(this);
    final GithubSettings settings = GithubSettings.getInstance();
    myGithubLoginPanel.setHost(settings.getHost());
    myGithubLoginPanel.setLogin(settings.getLogin());
    myGithubLoginPanel.setPassword("");
    setTitle("Login to GitHub");
    setOKButtonText("Login");
    init();
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
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
    final GithubAuthData auth = myGithubLoginPanel.getAuthData();
    try {
      boolean loggedSuccessfully = GithubUtil.checkAuthData(auth);
      if (loggedSuccessfully) {
        final GithubSettings settings = GithubSettings.getInstance();
        settings.setAuthData(auth, myGithubLoginPanel.shouldSavePassword());
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

  @NotNull
  public GithubAuthData getAuthData() {
    return myGithubLoginPanel.getAuthData();
  }
}