package org.jetbrains.plugins.github.tasks;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationCanceledException;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUtil;
import org.jetbrains.plugins.github.api.GithubApiUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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

    DocumentListener buttonUpdater = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateTokenButton();
      }
    };

    myRepoAuthor.getDocument().addDocumentListener(buttonUpdater);
    myRepoName.getDocument().addDocumentListener(buttonUpdater);
    myURLText.getDocument().addDocumentListener(buttonUpdater);

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
    myRepository.setRepoName(getRepoName());
    myRepository.setRepoAuthor(getRepoAuthor());
    myRepository.setToken(getToken());
    super.apply();
  }

  private void generateToken() {
    try {
      myToken.setText(
        GithubUtil.computeValueInModal(myProject, "Access to GitHub", new ThrowableConvertor<ProgressIndicator, String, IOException>() {
          @Override
          public String convert(ProgressIndicator indicator) throws IOException {
            return GithubUtil
              .runWithValidBasicAuthForHost(myProject, indicator, getHost(), new ThrowableConvertor<GithubAuthData, String, IOException>() {
                @NotNull
                @Override
                public String convert(GithubAuthData auth) throws IOException {
                  return GithubApiUtil.getReadOnlyToken(auth, getRepoAuthor(), getRepoName(), "Intellij tasks plugin");
                }
              });
          }
        }));
    }
    catch (GithubAuthenticationCanceledException ignore) {
    }
    catch (IOException e) {
      GithubNotifications.showErrorDialog(myProject, "Can't get access token", e);
    }
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    myRepoAuthorLabel.setAnchor(anchor);
    myRepoLabel.setAnchor(anchor);
    myTokenLabel.setAnchor(anchor);
  }

  private void updateTokenButton() {
    if (StringUtil.isEmptyOrSpaces(getHost()) ||
        StringUtil.isEmptyOrSpaces(getRepoAuthor()) ||
        StringUtil.isEmptyOrSpaces(getRepoName())) {
      myTokenButton.setEnabled(false);
    }
    else {
      myTokenButton.setEnabled(true);
    }
  }

  @NotNull
  private String getHost() {
    return myURLText.getText().trim();
  }

  @NotNull
  private String getRepoAuthor() {
    return myRepoAuthor.getText().trim();
  }

  @NotNull
  private String getRepoName() {
    return myRepoName.getText().trim();
  }

  @NotNull
  private String getToken() {
    return myToken.getText().trim();
  }
}
