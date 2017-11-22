/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.reset;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.RadioButtonEnumModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.util.VcsUserUtil;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;

public class GitNewResetDialog extends DialogWrapper {

  private static final String DIALOG_ID = "git.new.reset.dialog";
  
  @NotNull private final Project myProject;
  @NotNull private final Map<GitRepository, VcsFullCommitDetails> myCommits;
  @NotNull private final GitResetMode myDefaultMode;
  @NotNull private final ButtonGroup myButtonGroup;

  private RadioButtonEnumModel<GitResetMode> myEnumModel;

  protected GitNewResetDialog(@NotNull Project project, @NotNull Map<GitRepository, VcsFullCommitDetails> commits,
                              @NotNull GitResetMode defaultMode) {
    super(project);
    myProject = project;
    myCommits = commits;
    myDefaultMode = defaultMode;
    myButtonGroup = new ButtonGroup();

    init();
    setTitle("Git Reset");
    setOKButtonText("Reset");
    setOKButtonMnemonic('R');
    setResizable(false);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBag gb = new GridBag().
      setDefaultAnchor(GridBagConstraints.LINE_START).
      setDefaultInsets(0, UIUtil.DEFAULT_HGAP, UIUtil.LARGE_VGAP, 0);

    String description = prepareDescription(myProject, myCommits);
    panel.add(new JBLabel(XmlStringUtil.wrapInHtml(description)), gb.nextLine().next().coverLine());

    panel.add(new JBLabel(XmlStringUtil.wrapInHtmlLines(
      "This will reset the current branch head to the selected commit,",
      "and update the working tree and the index according to the selected mode:"
      ), UIUtil.ComponentStyle.SMALL), gb.nextLine().next().coverLine());

    for (GitResetMode mode : GitResetMode.values()) {
      JBRadioButton button = new JBRadioButton(mode.getName());
      button.setMnemonic(mode.getName().charAt(0));
      myButtonGroup.add(button);
      panel.add(button, gb.nextLine().next());
      panel.add(new JBLabel(XmlStringUtil.wrapInHtmlLines(mode.getDescription()), UIUtil.ComponentStyle.SMALL), gb.next());
    }

    myEnumModel = RadioButtonEnumModel.bindEnum(GitResetMode.class, myButtonGroup);
    myEnumModel.setSelected(myDefaultMode);
    return panel;
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return DIALOG_ID;
  }

  @NotNull
  private static String prepareDescription(@NotNull Project project, @NotNull Map<GitRepository, VcsFullCommitDetails> commits) {
    if (commits.size() == 1 && !isMultiRepo(project)) {
      Map.Entry<GitRepository, VcsFullCommitDetails> entry = commits.entrySet().iterator().next();
      return String.format("%s -> %s", getSourceText(entry.getKey()), getTargetText(entry.getValue()));
    }

    StringBuilder desc = new StringBuilder("");
    for (Map.Entry<GitRepository, VcsFullCommitDetails> entry : commits.entrySet()) {
      GitRepository repository = entry.getKey();
      VcsFullCommitDetails commit = entry.getValue();
      desc.append(String.format("%s in %s -> %s<br/>", getSourceText(repository),
                                getShortRepositoryName(repository), getTargetText(commit)));
    }
    return desc.toString();
  }

  @NotNull
  private static String getTargetText(@NotNull VcsFullCommitDetails commit) {
    String commitMessage = StringUtil.escapeXml(StringUtil.shortenTextWithEllipsis(commit.getSubject(), 20, 0));
    return String.format("<code><b>%s</b> \"%s\"</code> by <code>%s</code>",
                         commit.getId().toShortString(), commitMessage, VcsUserUtil.getShortPresentation(commit.getAuthor()));
  }

  @NotNull
  private static String getSourceText(@NotNull GitRepository repository) {
    String currentRevision = repository.getCurrentRevision();
    assert currentRevision != null;
    String text = repository.getCurrentBranch() == null ?
                  "HEAD (" + DvcsUtil.getShortHash(currentRevision) + ")" :
                  repository.getCurrentBranch().getName();
    return "<b>" + text + "</b>";
  }

  private static boolean isMultiRepo(@NotNull Project project) {
    return GitRepositoryManager.getInstance(project).moreThanOneRoot();
  }

  @NotNull
  public GitResetMode getResetMode() {
    return myEnumModel.getSelected();
  }

}
