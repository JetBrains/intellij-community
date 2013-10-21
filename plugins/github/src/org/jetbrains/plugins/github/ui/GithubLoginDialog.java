package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubSettings;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.swing.*;
import java.io.IOException;

/**
 * @author oleg
 * @date 10/20/10
 */
public class GithubLoginDialog extends DialogWrapper {

  protected static final Logger LOG = GithubUtil.LOG;

  protected final GithubLoginPanel myGithubLoginPanel;
  protected final GithubSettings mySettings;

  public GithubLoginDialog(@Nullable final Project project) {
    super(project, true);
    myGithubLoginPanel = new GithubLoginPanel(this);

    mySettings = GithubSettings.getInstance();
    myGithubLoginPanel.setHost(mySettings.getHost());
    myGithubLoginPanel.setLogin(mySettings.getLogin());
    myGithubLoginPanel.setAuthType(mySettings.getAuthType());

    if (mySettings.isSavePasswordMakesSense()) {
      myGithubLoginPanel.setSavePasswordSelected(mySettings.isSavePassword());
    }
    else {
      myGithubLoginPanel.setSavePasswordVisibleEnabled(false);
    }

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
    return myGithubLoginPanel.getPreferableFocusComponent();
  }

  @Override
  protected void doOKAction() {
    final GithubAuthData auth = myGithubLoginPanel.getAuthData();
    try {
      GithubUtil.checkAuthData(auth);

      saveCredentials(auth);
      if (mySettings.isSavePasswordMakesSense()) {
        mySettings.setSavePassword(myGithubLoginPanel.isSavePasswordSelected());
      }
      super.doOKAction();
    }
    catch (IOException e) {
      LOG.info(e);
      setErrorText("Can't login: " + GithubUtil.getErrorTextFromException(e));
    }
  }

  protected void saveCredentials(GithubAuthData auth) {
    final GithubSettings settings = GithubSettings.getInstance();
    settings.setCredentials(myGithubLoginPanel.getHost(), auth, myGithubLoginPanel.isSavePasswordSelected());
  }

  public void clearErrors() {
    setErrorText(null);
  }

  @NotNull
  public GithubAuthData getAuthData() {
    return myGithubLoginPanel.getAuthData();
  }

  public void lockHost(String host) {
    myGithubLoginPanel.lockHost(host);
  }
}