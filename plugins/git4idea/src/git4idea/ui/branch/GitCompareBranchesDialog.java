/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.ui.branch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import git4idea.ui.GitCommitListPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * Dialog for comparing two Git branches.
 * @author Kirill Likhodedov
 */
public class GitCompareBranchesDialog extends DialogWrapper {

  private final Project myProject;
  private final GitRepository myRepository;
  private final String myBranchName;
  private final List<GitCommit> myHeadToBranch;
  private final List<GitCommit> myBranchToHead;

  private final JPanel myRootPanel;
  private GitCommitListPanel myHeadToBranchListPanel;
  private GitCommitListPanel myBranchToHeadListPanel;

  public GitCompareBranchesDialog(GitRepository repository, String branchName, List<GitCommit> headToBranch, List<GitCommit> branchToHead) {
    super(repository.getProject(), false);
    myProject = repository.getProject();
    myRepository = repository;
    myBranchName = branchName;
    myHeadToBranch = headToBranch;
    myBranchToHead = branchToHead;

    String currentBranch = GitBranchUiUtil.getBranchNameOrRev(myRepository);

    myRootPanel = layoutComponents(currentBranch);

    setTitle(String.format("Comparing %s with %s", currentBranch, branchName));
    init();
  }

  private JPanel layoutComponents(String currentBranch) {
    final ChangesBrowser changesBrowser = new ChangesBrowser(myProject, null, Collections.<Change>emptyList(), null, false, true, null, ChangesBrowser.MyUseCase.COMMITTED_CHANGES);

    myHeadToBranchListPanel = new GitCommitListPanel(myProject, myHeadToBranch);
    myBranchToHeadListPanel = new GitCommitListPanel(myProject, myBranchToHead);

    addSelectionListener(myHeadToBranchListPanel, myBranchToHeadListPanel, changesBrowser);
    addSelectionListener(myBranchToHeadListPanel, myHeadToBranchListPanel, changesBrowser);

    JPanel htb = layoutCommitListPanel(currentBranch, true);
    JPanel bth = layoutCommitListPanel(currentBranch, false);

    Splitter lists = new Splitter(true, calcProportion());
    lists.setFirstComponent(htb);
    lists.setSecondComponent(bth);

    Splitter rootPanel = new Splitter(false, 0.7f);
    rootPanel.setSecondComponent(changesBrowser);
    rootPanel.setFirstComponent(lists);
    return rootPanel;
  }

  private static void addSelectionListener(@NotNull GitCommitListPanel sourcePanel,
                                           @NotNull final GitCommitListPanel otherPanel,
                                           @NotNull final ChangesBrowser changesBrowser) {
    sourcePanel.addListSelectionListener(new Consumer<GitCommit>() {
      @Override
      public void consume(GitCommit commit) {
        changesBrowser.setChangesToDisplay(commit.getChanges());
        otherPanel.clearSelection();
      }
    });
  }

  private JPanel layoutCommitListPanel(@NotNull String currentBranch, boolean forward) {
    String desc = makeDescription(currentBranch, forward);

    JPanel bth = new JPanel(new BorderLayout());
    JBLabel descriptionLabel = new JBLabel(desc, UIUtil.ComponentStyle.SMALL);
    descriptionLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
    bth.add(descriptionLabel, BorderLayout.NORTH);
    bth.add(forward ? myHeadToBranchListPanel : myBranchToHeadListPanel);
    return bth;
  }

  private String makeDescription(@NotNull String currentBranch, boolean forward) {
    String firstBranch = forward ? currentBranch : myBranchName;
    String secondBranch = forward ? myBranchName : currentBranch;
    return String.format("<html>Commits that exist in <code><b>%s</b></code> but don't exist in <code><b>%s</b></code> (<code>git log %s..%s</code>):</html>",
                         firstBranch, secondBranch, firstBranch, secondBranch);
  }

  /**
   * Calculates the splitter proportion.
   * We don't want to have much empty space, when there is a few commits, with a scrollpane for other list with many.
   * @return Splitter proportion between upper (HEAD..branch) and lower (branch..HEAD) commit lists.
   */
  private float calcProportion() {
    int totalSize = myBranchToHead.size() + myHeadToBranch.size();
    float prop = ((float)myHeadToBranch.size()) / ((float)totalSize);

    // but don't make less than 30% to leave at least a few lines.
    if (prop < 0.3f) {
      return 0.3f;
    } else if (prop > 0.7f) {
      return 0.7f;
    }
    return prop;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  // it is information dialog - no need to OK or Cancel. Close the dialog by clicking the cross button or pressing Esc.
  @Override
  protected Action[] createActions() {
    return new Action[0];
  }
}
