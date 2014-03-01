package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ThrowableConvertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.GithubUser;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubAuthDataHolder;
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

  protected final Project myProject;

  protected GithubAuthData myAuthData;

  public GithubLoginDialog(@NotNull final Project project, @NotNull GithubAuthData oldAuthData) {
    super(project, true);
    myProject = project;

    myGithubLoginPanel = new GithubLoginPanel(this);

    myGithubLoginPanel.setHost(oldAuthData.getHost());
    myGithubLoginPanel.setAuthType(oldAuthData.getAuthType());
    GithubAuthData.BasicAuth basicAuth = oldAuthData.getBasicAuth();
    if (basicAuth != null) {
      myGithubLoginPanel.setLogin(basicAuth.getLogin());
    }

    mySettings = GithubSettings.getInstance();
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
    final GithubAuthDataHolder authHolder = new GithubAuthDataHolder(myGithubLoginPanel.getAuthData());
    try {
      GithubUtil.computeValueInModal(myProject, "Access to GitHub", new ThrowableConvertor<ProgressIndicator, GithubUser, IOException>() {
        @NotNull
        @Override
        public GithubUser convert(ProgressIndicator indicator) throws IOException {
          return GithubUtil.checkAuthData(myProject, authHolder, indicator);
        }
      });

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