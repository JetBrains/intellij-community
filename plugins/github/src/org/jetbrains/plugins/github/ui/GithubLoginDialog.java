package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.util.*;

import javax.swing.*;
import java.io.IOException;

public class GithubLoginDialog extends DialogWrapper {
  private static final Logger LOG = GithubUtil.LOG;

  private final GithubCredentialsPanel myCredentialsPanel;

  @NotNull private final Project myProject;
  @NotNull private final AuthLevel myAuthLevel;

  private GithubAuthData myAuthData;
  private boolean mySavePassword;

  public GithubLoginDialog(@NotNull Project project, @NotNull GithubAuthData oldAuthData, @NotNull AuthLevel authLevel) {
    super(project, true);
    myProject = project;
    myAuthLevel = authLevel;

    myCredentialsPanel = new GithubCredentialsPanel(project);
    myCredentialsPanel.setTestButtonVisible(false);

    myCredentialsPanel.setHost(oldAuthData.getHost());
    myCredentialsPanel.setAuthType(oldAuthData.getAuthType());
    GithubAuthData.BasicAuth basicAuth = oldAuthData.getBasicAuth();
    if (basicAuth != null) {
      myCredentialsPanel.setLogin(basicAuth.getLogin());
    }

    if (authLevel.getHost() != null) myCredentialsPanel.lockHost(authLevel.getHost());
    if (authLevel.getAuthType() != null) myCredentialsPanel.lockAuthType(authLevel.getAuthType());

    if (!authLevel.isOnetime()) setDoNotAskOption(new MyRememberPasswordOption());

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
    return myCredentialsPanel;
  }

  @Override
  protected String getHelpId() {
    return "login_to_github";
  }

  @Override
  protected void doOKAction() {
    GithubAuthDataHolder authHolder = new GithubAuthDataHolder(myCredentialsPanel.getAuthData());
    try {
      GithubUtil.computeValueInModalIO(myProject, "Access to GitHub", indicator ->
        GithubUtil.checkAuthData(myProject, authHolder, indicator));

      myAuthData = authHolder.getAuthData();

      super.doOKAction();
    }
    catch (IOException e) {
      LOG.info(e);
      setErrorText("Can't login: " + GithubUtil.getErrorTextFromException(e));
    }
  }

  public boolean isSavePasswordSelected() {
    return mySavePassword;
  }

  @NotNull
  public GithubAuthData getAuthData() {
    if (myAuthData == null) {
      throw new IllegalStateException("AuthData is not set");
    }
    return myAuthData;
  }

  private class MyRememberPasswordOption implements DoNotAskOption {
    @Override
    public boolean isToBeShown() {
      return !GithubSettings.getInstance().isSavePassword();
    }

    @Override
    public void setToBeShown(boolean toBeShown, int exitCode) {
      mySavePassword = !toBeShown;
      GithubSettings.getInstance().setSavePassword(!toBeShown);
    }

    @Override
    public boolean canBeHidden() {
      return GithubSettings.getInstance().isSavePasswordMakesSense();
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
      return false;
    }

    @NotNull
    @Override
    public String getDoNotShowMessage() {
      return "Save credentials";
    }
  }
}