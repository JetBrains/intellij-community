// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.WindowWrapperBuilder;
import com.intellij.ui.TabbedPaneImpl;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgCommitCompareInfo;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;

public class HgCompareBranchesDialog {
  @NotNull private final Project myProject;

  @NotNull private final JPanel myLogPanel;
  @NotNull private final TabbedPaneImpl myTabbedPane;
  @NotNull private final String myTitle;

  @NotNull private final WindowWrapper.Mode myMode;

  private WindowWrapper myWrapper;

  public HgCompareBranchesDialog(@NotNull Project project,
                                 @NotNull String branchName,
                                 @NotNull String currentBranchName,
                                 @NotNull HgCommitCompareInfo compareInfo,
                                 @NotNull HgRepository initialRepo) {
    this(project, branchName, currentBranchName, compareInfo, initialRepo, false);
  }

  public HgCompareBranchesDialog(@NotNull Project project,
                                 @NotNull String branchName,
                                 @NotNull String currentBranchName,
                                 @NotNull HgCommitCompareInfo compareInfo,
                                 @NotNull HgRepository initialRepo,
                                 boolean dialog) {
    myProject = project;

    String rootString;
    if (compareInfo.getRepositories().size() == 1 && HgUtil.getRepositoryManager(myProject).moreThanOneRoot()) {
      rootString = " in root " + DvcsUtil.getShortRepositoryName(initialRepo);
    }
    else {
      rootString = "";
    }
    myTitle = String.format("Comparing %s with %s%s", currentBranchName, branchName, rootString);
    myMode = dialog ? WindowWrapper.Mode.MODAL : WindowWrapper.Mode.FRAME;

    JPanel diffPanel = new HgCompareBranchesDiffPanel(myProject, branchName, currentBranchName, compareInfo);
    myLogPanel = new HgCompareBranchesLogPanel(myProject, branchName, currentBranchName, compareInfo, initialRepo);

    myTabbedPane = new TabbedPaneImpl(SwingConstants.TOP);
    myTabbedPane.addTab("Log", VcsLogIcons.Branch, myLogPanel);
    myTabbedPane.addTab("Files", AllIcons.Actions.ListChanges, diffPanel);
    myTabbedPane.setKeyboardNavigation(TabbedPaneImpl.DEFAULT_PREV_NEXT_SHORTCUTS);
  }

  public void show() {
    if (myWrapper == null) {
      myWrapper = new WindowWrapperBuilder(myMode, myTabbedPane)
        .setProject(myProject)
        .setTitle(myTitle)
        .setPreferredFocusedComponent(myLogPanel)
        .setDimensionServiceKey(HgCompareBranchesDialog.class.getName())
        .build();
    }
    myWrapper.show();
  }
}
