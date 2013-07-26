package org.jetbrains.plugins.github.tasks;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.*;
import org.jetbrains.plugins.github.GithubAuthData;
import org.jetbrains.plugins.github.api.GithubApiUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Dennis.Ushakov
 */
public class GithubRepositoryEditor extends BaseRepositoryEditor<GithubRepository> {
  private JTextField myToken;
  private JTextField myRepoName;
  private JTextField myRepoAuthor;
  private JButton myTokenButton;
  private JBLabel myRepoAuthorLabel;
  private JBLabel myRepoLabel;
  private JCheckBox myPrivateRepo;

  public GithubRepositoryEditor(final Project project, final GithubRepository repository, Consumer<GithubRepository> changeListener) {
    super(project, repository, changeListener);
    myUserNameText.setVisible(false);
    myUsernameLabel.setVisible(false);
    myPasswordText.setVisible(false);
    myPasswordLabel.setVisible(false);

    myToken.setText(repository.getToken());
    myRepoAuthor.setText(repository.getRepoAuthor());
    myRepoName.setText(repository.getRepoName());
    myPrivateRepo.setSelected(false);

    setAnchor(myRepoAuthorLabel);
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myUrlLabel.setText("Host:");

    myRepoAuthorLabel = new JBLabel("Repository author:", SwingConstants.RIGHT);
    myRepoAuthor = new JTextField();
    installListener(myRepoAuthor);

    myRepoLabel = new JBLabel("Repository:", SwingConstants.RIGHT);
    myRepoName = new JTextField();
    installListener(myRepoName);

    myToken = new JTextField();
    installListener(myToken);
    myTokenButton = new JButton("API token");
    myTokenButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        generateToken();
        doApply();
      }
    });

    myPrivateRepo = new JCheckBox("Private repository");
    installListener(myPrivateRepo);

    return FormBuilder.createFormBuilder().setAlignLabelOnRight(true).addLabeledComponent(myTokenButton, myToken)
      .addLabeledComponent(myRepoAuthorLabel, myRepoAuthor).addLabeledComponent(myRepoLabel, myRepoName)
      .addComponentToRightColumn(myPrivateRepo).getPanel();
  }

  @Override
  public void apply() {
    myRepository.setRepoName(myRepoName.getText().trim());
    myRepository.setRepoAuthor(myRepoAuthor.getText().trim());
    myRepository.setToken(myToken.getText().trim());
    myUseHttpAuthenticationCheckBox.setSelected(!StringUtil.isEmpty(myUserNameText.getText()));
    super.apply();
  }

  @Override
  protected void afterTestConnection(final boolean connectionSuccessful) {
    if (connectionSuccessful) {
      final Ref<Collection<String>> scopesRef = new Ref<Collection<String>>();
      final Ref<IOException> exceptionRef = new Ref<IOException>();
      ProgressManager.getInstance().run(new Task.Modal(myProject, "Access to GitHub", true) {
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            scopesRef
              .set(GithubApiUtil.getTokenScopes(GithubAuthData.createTokenAuth(myURLText.getText().trim(), myToken.getText().trim())));
          }
          catch (IOException e) {
            exceptionRef.set(e);
          }
        }
      });
      if (!exceptionRef.isNull()) {
        GithubNotifications.showErrorDialog(myProject, "Can't check token scopes", exceptionRef.get());
        return;
      }
      Collection<String> scopes = scopesRef.get();
      if (myPrivateRepo.isSelected()) {
        scopes.remove("repo");
      }
      if (scopes.isEmpty()) {
        return;
      }
      GithubNotifications
        .showWarningDialog(myProject, "Unneeded token scopes detected", "Unneeded scopes: " + StringUtil.join(scopes, ", "));
    }
  }

  private void generateToken() {
    final Ref<String> tokenRef = new Ref<String>();
    final Ref<IOException> exceptionRef = new Ref<IOException>();
    final Collection<String> scopes = myPrivateRepo.isSelected() ? Collections.singleton("repo") : Collections.<String>emptyList();
    ProgressManager.getInstance().run(new Task.Modal(myProject, "Access to GitHub", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          tokenRef.set(GithubUtil.runWithValidBasicAuth(myProject, indicator, new ThrowableConvertor<GithubAuthData, String, IOException>() {
            @Nullable
            @Override
            public String convert(GithubAuthData auth) throws IOException {
              return GithubApiUtil.getScopedToken(auth, scopes, "Intellij tasks plugin");

            }
          }));
        }
        catch (IOException e) {
          exceptionRef.set(e);
        }
      }
    });
    if (!exceptionRef.isNull()) {
      if (exceptionRef.get() instanceof GithubAuthenticationCanceledException) {
        return;
      }
      GithubNotifications.showErrorDialog(myProject, "Can't get access token", exceptionRef.get());
      return;
    }
    myToken.setText(tokenRef.get());
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    myRepoAuthorLabel.setAnchor(anchor);
    myRepoLabel.setAnchor(anchor);
  }
}
