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
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.GitCommitListPanel;
import git4idea.ui.GitRepositoryComboboxListCellRenderer;
import git4idea.util.GitCommitCompareInfo;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Dialog for comparing two Git branches.
 * @author Kirill Likhodedov
 */
public class GitCompareBranchesDialog extends DialogWrapper {

  private final Project myProject;
  private final String myBranchName;
  private final String myCurrentBranchName;
  private final GitCommitCompareInfo myCompareInfo;
  private final GitRepository myInitialRepo;

  private GitCommitListPanel myHeadToBranchListPanel;
  private GitCommitListPanel myBranchToHeadListPanel;

  public GitCompareBranchesDialog(@NotNull Project project, @NotNull String branchName, @NotNull String currentBranchName,
                                  @NotNull GitCommitCompareInfo compareInfo, @NotNull GitRepository initialRepo) {
    super(project, false);
    myCurrentBranchName = currentBranchName;
    myCompareInfo = compareInfo;
    myProject = project;
    myBranchName = branchName;
    myInitialRepo = initialRepo;

    String rootString;
    if (compareInfo.getRepositories().size() == 1 && GitRepositoryManager.getInstance(myProject).moreThanOneRoot()) {
      rootString = " in root " + GitUIUtil.getShortRepositoryName(initialRepo);
    }
    else {
      rootString = "";
    }
    setTitle(String.format("Comparing %s with %s%s", currentBranchName, branchName, rootString));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    final ChangesBrowser changesBrowser = new ChangesBrowser(myProject, null, Collections.<Change>emptyList(), null, false, true, null, ChangesBrowser.MyUseCase.COMMITTED_CHANGES, null);

    myHeadToBranchListPanel = new GitCommitListPanel(myProject, getHeadToBranchCommits(myInitialRepo));
    myBranchToHeadListPanel = new GitCommitListPanel(myProject, getBranchToHeadCommits(myInitialRepo));

    addSelectionListener(myHeadToBranchListPanel, myBranchToHeadListPanel, changesBrowser);
    addSelectionListener(myBranchToHeadListPanel, myHeadToBranchListPanel, changesBrowser);

    JPanel htb = layoutCommitListPanel(myCurrentBranchName, true);
    JPanel bth = layoutCommitListPanel(myCurrentBranchName, false);

    Splitter lists = new Splitter(true, 0.5f);
    lists.setFirstComponent(htb);
    lists.setSecondComponent(bth);

    Splitter rootPanel = new Splitter(false, 0.7f);
    rootPanel.setSecondComponent(changesBrowser);
    rootPanel.setFirstComponent(lists);
    return rootPanel;
  }

  @Override
  protected JComponent createNorthPanel() {
    final JComboBox repoSelector = new JComboBox(ArrayUtil.toObjectArray(myCompareInfo.getRepositories(), GitRepository.class));
    repoSelector.setRenderer(new GitRepositoryComboboxListCellRenderer(repoSelector));
    repoSelector.setSelectedItem(myInitialRepo);
    
    repoSelector.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        GitRepository selectedRepo = (GitRepository)repoSelector.getSelectedItem();
        myHeadToBranchListPanel.setCommits(getHeadToBranchCommits(selectedRepo));
        myBranchToHeadListPanel.setCommits(getBranchToHeadCommits(selectedRepo));
      }
    });
    
    JPanel repoSelectorPanel = new JPanel(new BorderLayout());
    JBLabel label = new JBLabel("Repository: ");
    label.setLabelFor(repoSelectorPanel);
    repoSelectorPanel.add(label);
    repoSelectorPanel.add(repoSelector);

    if (myCompareInfo.getRepositories().size() < 2) {
      repoSelectorPanel.setVisible(false);
    }
    return repoSelectorPanel;
  }

  private ArrayList<GitCommit> getBranchToHeadCommits(GitRepository selectedRepo) {
    return new ArrayList<GitCommit>(myCompareInfo.getBranchToHeadCommits(selectedRepo));
  }

  private ArrayList<GitCommit> getHeadToBranchCommits(GitRepository selectedRepo) {
    return new ArrayList<GitCommit>(myCompareInfo.getHeadToBranchCommits(selectedRepo));
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
                         secondBranch, firstBranch, firstBranch, secondBranch);
  }


  // it is information dialog - no need to OK or Cancel. Close the dialog by clicking the cross button or pressing Esc.
  @Override
  protected Action[] createActions() {
    return new Action[0];
  }

  @Override
  protected String getDimensionServiceKey() {
    return GitCompareBranchesDialog.class.getName();
  }

}
