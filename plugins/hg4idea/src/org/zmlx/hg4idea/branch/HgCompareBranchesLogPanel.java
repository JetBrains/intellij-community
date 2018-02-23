// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.branch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.log.HgCommit;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgCommitListPanel;
import org.zmlx.hg4idea.ui.HgRepositoryComboboxListCellRenderer;
import org.zmlx.hg4idea.util.HgCommitCompareInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

class HgCompareBranchesLogPanel extends JPanel {

  private final Project myProject;
  private final String myBranchName;
  private final String myCurrentBranchName;
  private final HgCommitCompareInfo myCompareInfo;
  private final HgRepository myInitialRepo;

  private HgCommitListPanel myHeadToBranchListPanel;
  private HgCommitListPanel myBranchToHeadListPanel;

  HgCompareBranchesLogPanel(@NotNull Project project, @NotNull String branchName, @NotNull String currentBranchName,
                             @NotNull HgCommitCompareInfo compareInfo, @NotNull HgRepository initialRepo) {
    super(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    myProject = project;
    myBranchName = branchName;
    myCurrentBranchName = currentBranchName;
    myCompareInfo = compareInfo;
    myInitialRepo = initialRepo;

    add(createNorthPanel(), BorderLayout.NORTH);
    add(createCenterPanel());
  }

  private JComponent createCenterPanel() {
    final SimpleChangesBrowser changesBrowser = new SimpleChangesBrowser(myProject, false, true);

    myHeadToBranchListPanel = new HgCommitListPanel(getHeadToBranchCommits(myInitialRepo),
                                                     String.format("Branch %s is fully merged to %s", myBranchName, myCurrentBranchName));
    myBranchToHeadListPanel = new HgCommitListPanel(getBranchToHeadCommits(myInitialRepo),
                                                     String.format("Branch %s is fully merged to %s", myCurrentBranchName, myBranchName));

    addSelectionListener(myHeadToBranchListPanel, myBranchToHeadListPanel, changesBrowser);
    addSelectionListener(myBranchToHeadListPanel, myHeadToBranchListPanel, changesBrowser);

    myHeadToBranchListPanel.registerDiffAction(changesBrowser.getDiffAction());
    myBranchToHeadListPanel.registerDiffAction(changesBrowser.getDiffAction());

    JPanel htb = layoutCommitListPanel(true);
    JPanel bth = layoutCommitListPanel(false);

    JPanel listPanel = null;
    switch (getInfoType()) {
      case HEAD_TO_BRANCH:
        listPanel = htb;
        break;
      case BRANCH_TO_HEAD:
        listPanel = bth;
        break;
      case BOTH:
        Splitter lists = new Splitter(true, 0.5f);
        lists.setFirstComponent(htb);
        lists.setSecondComponent(bth);
        listPanel = lists;
    }

    Splitter rootPanel = new Splitter(false, 0.7f);
    rootPanel.setSecondComponent(changesBrowser);
    rootPanel.setFirstComponent(listPanel);
    return rootPanel;
  }

  private JComponent createNorthPanel() {
    final ComboBox<HgRepository> repoSelector = new ComboBox<>(ArrayUtil.toObjectArray(myCompareInfo.getRepositories(), HgRepository.class));
    repoSelector.setRenderer(new HgRepositoryComboboxListCellRenderer());
    repoSelector.setSelectedItem(myInitialRepo);

    repoSelector.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        HgRepository selectedRepo = (HgRepository)repoSelector.getSelectedItem();
        myHeadToBranchListPanel.setCommits(getHeadToBranchCommits(selectedRepo));
        myBranchToHeadListPanel.setCommits(getBranchToHeadCommits(selectedRepo));
      }
    });

    JPanel repoSelectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    JBLabel label = new JBLabel("Repository: ");
    label.setLabelFor(repoSelectorPanel);
    label.setDisplayedMnemonic(KeyEvent.VK_R);
    repoSelectorPanel.add(label);
    repoSelectorPanel.add(repoSelector);

    if (myCompareInfo.getRepositories().size() < 2) {
      repoSelectorPanel.setVisible(false);
    }
    return repoSelectorPanel;
  }

  private ArrayList<HgCommit> getBranchToHeadCommits(HgRepository selectedRepo) {
    return new ArrayList<>(myCompareInfo.getBranchToHeadCommits(selectedRepo));
  }

  private ArrayList<HgCommit> getHeadToBranchCommits(HgRepository selectedRepo) {
    return new ArrayList<>(myCompareInfo.getHeadToBranchCommits(selectedRepo));
  }

  private HgCommitCompareInfo.InfoType getInfoType() {
    return myCompareInfo.getInfoType();
  }

  private static void addSelectionListener(@NotNull HgCommitListPanel sourcePanel,
                                           @NotNull final HgCommitListPanel otherPanel,
                                           @NotNull final SimpleChangesBrowser changesBrowser) {
    sourcePanel.addListMultipleSelectionListener(changes -> {
      changesBrowser.setChangesToDisplay(changes);
      otherPanel.clearSelection();
    });
  }

  private JPanel layoutCommitListPanel(boolean forward) {
    String desc = makeDescription(forward);

    JPanel bth = new JPanel(new BorderLayout());
    JBLabel descriptionLabel = new JBLabel(desc, UIUtil.ComponentStyle.SMALL);
    descriptionLabel.setBorder(JBUI.Borders.emptyBottom(5));
    bth.add(descriptionLabel, BorderLayout.NORTH);
    bth.add(forward ? myHeadToBranchListPanel : myBranchToHeadListPanel);
    return bth;
  }

  private String makeDescription(boolean forward) {
    String firstBranch = forward ? myCurrentBranchName : myBranchName;
    String secondBranch = forward ? myBranchName : myCurrentBranchName;
    return String.format("<html>Commits that exist in <code><b>%s</b></code> but don't exist in <code><b>%s</b></code> (<code>hg log -r \"reverse(%s%%%s)\"</code>):</html>",
                         secondBranch, firstBranch, secondBranch, firstBranch);
  }
}

