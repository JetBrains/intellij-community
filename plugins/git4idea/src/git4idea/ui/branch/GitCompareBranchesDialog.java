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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.ui.TabbedPaneImpl;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.util.GitCommitCompareInfo;
import icons.Git4ideaIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Dialog for comparing two Git branches.
 */
public class GitCompareBranchesDialog extends FrameWrapper {

  @NotNull private final Project myProject;
  @NotNull private final String myBranchName;
  @NotNull private final String myCurrentBranchName;
  @NotNull private final GitCommitCompareInfo myCompareInfo;
  @NotNull private final JPanel myLogPanel;

  public GitCompareBranchesDialog(@NotNull Project project, @NotNull String branchName, @NotNull String currentBranchName,
                                  @NotNull GitCommitCompareInfo compareInfo, @NotNull GitRepository initialRepo) {
    super(project, GitCompareBranchesDialog.class.getName());
    myCurrentBranchName = currentBranchName;
    myCompareInfo = compareInfo;
    myProject = project;
    myBranchName = branchName;

    String rootString;
    if (compareInfo.getRepositories().size() == 1 && GitUtil.getRepositoryManager(myProject).moreThanOneRoot()) {
      rootString = " in root " + DvcsUtil.getShortRepositoryName(initialRepo);
    }
    else {
      rootString = "";
    }
    setTitle(String.format("Comparing %s with %s%s", currentBranchName, branchName, rootString));

    myLogPanel = new GitCompareBranchesLogPanel(myProject, myBranchName, myCurrentBranchName, myCompareInfo, initialRepo);
    setPreferredFocusedComponent(myLogPanel);
    setComponent(createCenterPanel());
    closeOnEsc();
  }

  @NotNull
  protected JComponent createCenterPanel() {
    JPanel diffPanel = new GitCompareBranchesDiffPanel(myProject, myBranchName, myCurrentBranchName, myCompareInfo);

    TabbedPaneImpl tabbedPane = new TabbedPaneImpl(SwingConstants.TOP);
    tabbedPane.addTab("Log", Git4ideaIcons.Branch, myLogPanel);
    tabbedPane.addTab("Diff", AllIcons.Actions.Diff, diffPanel);
    tabbedPane.setKeyboardNavigation(TabbedPaneImpl.DEFAULT_PREV_NEXT_SHORTCUTS);
    return tabbedPane;
  }

}
