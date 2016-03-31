package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.util.*;

import javax.swing.*;
import java.io.IOException;

/**
 * @author oleg
 * @date 10/20/10
 */
public class GithubLoginDialog extends DialogWrapper {

  private static final Logger LOG = GithubUtil.LOG;

  private final GithubLoginPanel myGithubLoginPanel;
  private final GithubSettings mySettings;

  @NotNull private final Project myProject;
  @NotNull private final AuthLevel myAuthLevel;

  protected GithubAuthData myAuthData;

  public GithubLoginDialog(@NotNull final Project project, @NotNull GithubAuthData oldAuthData, @NotNull AuthLevel authLevel) {
    super(project, true);
    myProject = project;
    myAuthLevel = authLevel;

    myGithubLoginPanel = new GithubLoginPanel(this);

    myGithubLoginPanel.setHost(oldAuthData.getHost());
    myGithubLoginPanel.setAuthType(oldAuthData.getAuthType());
    GithubAuthData.BasicAuth basicAuth = oldAuthData.getBasicAuth();
    if (basicAuth != null) {
      myGithubLoginPanel.setLogin(basicAuth.getLogin());
    }

    mySettings = GithubSettings.getInstance();
    if (mySettings.isSavePasswordMakesSense() && !authLevel.isOnetime()) {
      myGithubLoginPanel.setSavePasswordSelected(mySettings.isSavePassword());
    }
    else {
      myGithubLoginPanel.setSavePasswordVisibleEnabled(false);
    }

    if (authLevel.getHost() != null) myGithubLoginPanel.lockHost(authLevel.getHost());
    if (authLevel.getAuthType() != null) myGithubLoginPanel.lockAuthType(authLevel.getAuthType());

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
    final GithubAuthDataHolder authHolder = new GithubAuthDataHolder(myGithubLoginPanel.getAuthData());
    try {
      GithubUtil.computeValueInModalIO(myProject, "Access to GitHub", indicator ->
        GithubUtil.checkAuthData(myProject, authHolder, indicator));

      myAuthData = authHolder.getAuthData();

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

  public boolean isSavePasswordSelected() {
    return myGithubLoginPanel.isSavePasswordSelected();
  }

  @NotNull
  public GithubAuthData getAuthData() {
    if (myAuthData == null) {
      throw new IllegalStateException("AuthData is not set");
    }
    return myAuthData;
  }

  public void clearErrors() {
    setErrorText(null);
  }
}