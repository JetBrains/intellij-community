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
import org.jetbrains.plugins.github.GithubAuthData;
import org.jetbrains.plugins.github.GithubAuthenticationCanceledException;
import org.jetbrains.plugins.github.GithubNotifications;
import org.jetbrains.plugins.github.GithubUtil;
import org.jetbrains.plugins.github.api.GithubApiUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

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
  private JBLabel myTokenLabel;

  public GithubRepositoryEditor(final Project project, final GithubRepository repository, Consumer<GithubRepository> changeListener) {
    super(project, repository, changeListener);
    myUserNameText.setVisible(false);
    myUsernameLabel.setVisible(false);
    myPasswordText.setVisible(false);
    myPasswordLabel.setVisible(false);
    myUseHttpAuthenticationCheckBox.setVisible(false);

    myToken.setText(repository.getToken());
    myRepoAuthor.setText(repository.getRepoAuthor());
    myRepoName.setText(repository.getRepoName());

    setAnchor(myRepoAuthorLabel);
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myUrlLabel.setText("Host:");

    myRepoAuthorLabel = new JBLabel("Repository Owner:", SwingConstants.RIGHT);
    myRepoAuthor = new JTextField();
    installListener(myRepoAuthor);

    myRepoLabel = new JBLabel("Repository:", SwingConstants.RIGHT);
    myRepoName = new JTextField();
    installListener(myRepoName);

    myTokenLabel = new JBLabel("API Token:", SwingConstants.RIGHT);
    myToken = new JTextField();
    installListener(myToken);
    myTokenButton = new JButton("Create API token");
    myTokenButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        generateToken();
        doApply();
      }
    });

    JPanel myTokenPanel = new JPanel();
    myTokenPanel.setLayout(new BorderLayout(5, 5));
    myTokenPanel.add(myToken, BorderLayout.CENTER);
    myTokenPanel.add(myTokenButton, BorderLayout.EAST);

    return FormBuilder.createFormBuilder().setAlignLabelOnRight(true).addLabeledComponent(myRepoAuthorLabel, myRepoAuthor)
      .addLabeledComponent(myRepoLabel, myRepoName).addLabeledComponent(myTokenLabel, myTokenPanel).getPanel();
  }

  @Override
  public void apply() {
    myRepository.setRepoName(myRepoName.getText().trim());
    myRepository.setRepoAuthor(myRepoAuthor.getText().trim());
    myRepository.setToken(myToken.getText().trim());
    super.apply();
  }

  private void generateToken() {
    final Ref<String> tokenRef = new Ref<String>();
    final Ref<IOException> exceptionRef = new Ref<IOException>();
    ProgressManager.getInstance().run(new Task.Modal(myProject, "Access to GitHub", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          tokenRef.set(GithubUtil.runWithValidBasicAuth(myProject, indicator, new ThrowableConvertor<GithubAuthData, String, IOException>() {
            @NotNull
            @Override
            public String convert(GithubAuthData auth) throws IOException {
              return GithubApiUtil.getReadOnlyToken(auth, myRepoAuthor.getText(), myRepoName.getText(), "Intellij tasks plugin");
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
    myTokenLabel.setAnchor(anchor);
  }
}
