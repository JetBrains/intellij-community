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
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.TabbedPaneImpl;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitCommitCompareInfo;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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
  private JPanel myLogPanel;

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
    myLogPanel = new GitCompareBranchesLogPanel(myProject, myBranchName, myCurrentBranchName, myCompareInfo, myInitialRepo);
    JPanel diffPanel = new GitCompareBranchesDiffPanel(myProject, myBranchName, myCurrentBranchName, myCompareInfo);

    TabbedPaneImpl tabbedPane = new TabbedPaneImpl(SwingConstants.TOP);
    tabbedPane.addTab("Log", IconLoader.getIcon("/icons/branch.png"), myLogPanel);
    tabbedPane.addTab("Diff", IconLoader.getIcon("/actions/diff.png"), diffPanel);
    tabbedPane.setKeyboardNavigation(TabbedPaneImpl.DEFAULT_PREV_NEXT_SHORTCUTS);
    return tabbedPane;
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

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLogPanel;
  }
}
