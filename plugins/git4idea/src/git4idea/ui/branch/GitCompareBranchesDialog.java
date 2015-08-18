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
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.WindowWrapper.Mode;
import com.intellij.openapi.ui.WindowWrapperBuilder;
import com.intellij.ui.TabbedPaneImpl;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.util.GitCommitCompareInfo;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Dialog for comparing two Git branches.
 */
public class GitCompareBranchesDialog {
  @NotNull private final Project myProject;

  @NotNull private final JPanel myLogPanel;
  @NotNull private final TabbedPaneImpl myTabbedPane;
  @NotNull private final String myTitle;

  @NotNull private final Mode myMode;

  private WindowWrapper myWrapper;

  public GitCompareBranchesDialog(@NotNull Project project,
                                  @NotNull String branchName,
                                  @NotNull String currentBranchName,
                                  @NotNull GitCommitCompareInfo compareInfo,
                                  @NotNull GitRepository initialRepo) {
    this(project, branchName, currentBranchName, compareInfo, initialRepo, false);
  }

  public GitCompareBranchesDialog(@NotNull Project project,
                                  @NotNull String branchName,
                                  @NotNull String currentBranchName,
                                  @NotNull GitCommitCompareInfo compareInfo,
                                  @NotNull GitRepository initialRepo,
                                  boolean dialog) {
    myProject = project;

    String rootString;
    if (compareInfo.getRepositories().size() == 1 && GitUtil.getRepositoryManager(myProject).moreThanOneRoot()) {
      rootString = " in root " + DvcsUtil.getShortRepositoryName(initialRepo);
    }
    else {
      rootString = "";
    }
    myTitle = String.format("Comparing %s with %s%s", currentBranchName, branchName, rootString);
    myMode = dialog ? Mode.MODAL : Mode.FRAME;

    JPanel diffPanel = new GitCompareBranchesDiffPanel(myProject, branchName, currentBranchName, compareInfo);
    myLogPanel = new GitCompareBranchesLogPanel(myProject, branchName, currentBranchName, compareInfo, initialRepo);

    myTabbedPane = new TabbedPaneImpl(SwingConstants.TOP);
    myTabbedPane.addTab("Log", VcsLogIcons.Branch, myLogPanel);
    myTabbedPane.addTab("Diff", AllIcons.Actions.Diff, diffPanel);
    myTabbedPane.setKeyboardNavigation(TabbedPaneImpl.DEFAULT_PREV_NEXT_SHORTCUTS);
  }

  public void show() {
    if (myWrapper == null) {
      myWrapper = new WindowWrapperBuilder(myMode, myTabbedPane)
        .setProject(myProject)
        .setTitle(myTitle)
        .setPreferredFocusedComponent(myLogPanel)
        .setDimensionServiceKey(GitCompareBranchesDialog.class.getName())
        .build();
    }
    myWrapper.show();
  }
}
