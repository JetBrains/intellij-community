package org.jetbrains.plugins.github.tasks;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dennis.Ushakov
 */
public class GitHubRepositoryEditor extends BaseRepositoryEditor<GitHubRepository> {
  private JTextField myRepoName;
  private JTextField myRepoAuthor;
  private JBLabel myRepositoryAuthorLabel;
  private JBLabel myRepositoryLabel;

  public GitHubRepositoryEditor(final Project project,
                                final GitHubRepository repository,
                                Consumer<GitHubRepository> changeListener) {
    super(project, repository, changeListener);

    // project author, by default same as username
    myRepoAuthor.setText(repository.getRepoAuthor());

    // project id
    myRepoName.setText(repository.getRepoName());

    myUrlLabel.setVisible(false);
    myURLText.setVisible(false);

    setAnchor(myRepositoryAuthorLabel);
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myRepositoryAuthorLabel = new JBLabel("Repository author:", SwingConstants.RIGHT);
    myRepoAuthor = new JTextField();
    installListener(myRepoAuthor);

    myRepositoryLabel = new JBLabel("Repository:", SwingConstants.RIGHT);
    myRepoName = new JTextField();
    installListener(myRepoName);

    return FormBuilder.createFormBuilder().addLabeledComponent(myRepositoryAuthorLabel, myRepoAuthor)
      .addLabeledComponent(myRepositoryLabel, myRepoName).getPanel();
  }

  @Override
  public void apply() {
    myRepository.setRepoName(myRepoName.getText().trim());
    myRepository.setRepoAuthor(myRepoAuthor.getText().trim());
    myUseHTTPAuthentication.setSelected(!StringUtil.isEmpty(myUserNameText.getText()));
    super.apply();
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    myRepositoryAuthorLabel.setAnchor(anchor);
    myRepositoryLabel.setAnchor(anchor);
  }
}
