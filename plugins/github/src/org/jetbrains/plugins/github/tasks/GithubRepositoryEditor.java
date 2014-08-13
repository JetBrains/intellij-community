package org.jetbrains.plugins.github.tasks;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.exceptions.GithubOperationCanceledException;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubAuthDataHolder;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUtil;

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
  private MyTextField myRepoAuthor;
  private MyTextField myRepoName;
  private MyTextField myToken;
  private JButton myTokenButton;
  private JBLabel myHostLabel;
  private JBLabel myRepositoryLabel;
  private JBLabel myTokenLabel;

  public GithubRepositoryEditor(final Project project, final GithubRepository repository, Consumer<GithubRepository> changeListener) {
    super(project, repository, changeListener);
    myUrlLabel.setVisible(false);
    myUsernameLabel.setVisible(false);
    myUserNameText.setVisible(false);
    myPasswordLabel.setVisible(false);
    myPasswordText.setVisible(false);
    myUseHttpAuthenticationCheckBox.setVisible(false);

    myRepoAuthor.setText(repository.getRepoAuthor());
    myRepoName.setText(repository.getRepoName());
    myToken.setText(repository.getToken());

    DocumentListener buttonUpdater = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateTokenButton();
      }
    };

    myURLText.getDocument().addDocumentListener(buttonUpdater);
    myRepoAuthor.getDocument().addDocumentListener(buttonUpdater);
    myRepoName.getDocument().addDocumentListener(buttonUpdater);
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myHostLabel = new JBLabel("Host:", SwingConstants.RIGHT);

    JPanel myHostPanel = new JPanel(new BorderLayout(5, 0));
    myHostPanel.add(myURLText, BorderLayout.CENTER);
    myHostPanel.add(myShareUrlCheckBox, BorderLayout.EAST);

    myRepositoryLabel = new JBLabel("Repository:", SwingConstants.RIGHT);
    myRepoAuthor = new MyTextField("Repository Owner");
    myRepoName = new MyTextField("Repository Name");
    myRepoAuthor.setPreferredSize("SomelongNickname");
    myRepoName.setPreferredSize("SomelongReponame-with-suffixes");

    JPanel myRepoPanel = new JPanel(new GridBagLayout());
    GridBag bag = new GridBag().setDefaultWeightX(1).setDefaultFill(GridBagConstraints.HORIZONTAL);
    myRepoPanel.add(myRepoAuthor, bag.nextLine().next());
    myRepoPanel.add(new JLabel("/"), bag.next().fillCellNone().insets(0, 5, 0, 5).weightx(0));
    myRepoPanel.add(myRepoName, bag.next());

    myTokenLabel = new JBLabel("API Token:", SwingConstants.RIGHT);
    myToken = new MyTextField("OAuth2 token");
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

    installListener(myRepoAuthor);
    installListener(myRepoName);
    installListener(myToken);

    return FormBuilder.createFormBuilder().setAlignLabelOnRight(true).addLabeledComponent(myHostLabel, myHostPanel)
      .addLabeledComponent(myRepositoryLabel, myRepoPanel).addLabeledComponent(myTokenLabel, myTokenPanel).getPanel();
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
          @NotNull
          @Override
          public String convert(ProgressIndicator indicator) throws IOException {
            return GithubUtil
              .runTaskWithBasicAuthForHost(myProject, GithubAuthDataHolder.createFromSettings(), indicator, getHost(),
                                           new ThrowableConvertor<GithubAuthData, String, IOException>() {
                                             @NotNull
                                             @Override
                                             public String convert(@NotNull GithubAuthData auth) throws IOException {
                                               return GithubApiUtil
                                                 .getReadOnlyToken(auth, getRepoAuthor(), getRepoName(), "IntelliJ tasks plugin");
                                             }
                                           }
              );
          }
        })
      );
    }
    catch (IOException e) {
      GithubNotifications.showErrorDialog(myProject, "Can't get access token", e);
    }
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    myHostLabel.setAnchor(anchor);
    myRepositoryLabel.setAnchor(anchor);
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

  public static class MyTextField extends JBTextField {
    private int myWidth = -1;

    public MyTextField(@NotNull String hintCaption) {
      getEmptyText().setText(hintCaption);
    }

    public void setPreferredSize(@NotNull String sampleSizeString) {
      myWidth = getFontMetrics(getFont()).stringWidth(sampleSizeString);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      if (myWidth != -1) {
        size.width = myWidth;
      }
      return size;
    }
  }
}
