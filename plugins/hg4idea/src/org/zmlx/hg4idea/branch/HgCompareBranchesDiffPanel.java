// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.branch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.util.HgCommitCompareInfo;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.emptyList;

class HgCompareBranchesDiffPanel extends JPanel {

  private final String myBranchName;
  private final String myCurrentBranchName;
  private final HgCommitCompareInfo myCompareInfo;
  private final HgProjectSettings myVcsSettings;

  private final JBLabel myLabel;
  private final MyChangesBrowser myChangesBrowser;

  public HgCompareBranchesDiffPanel(Project project, String branchName, String currentBranchName, HgCommitCompareInfo compareInfo) {
    myCurrentBranchName = currentBranchName;
    myCompareInfo = compareInfo;
    myBranchName = branchName;
    myVcsSettings = HgProjectSettings.getInstance(project);

    myLabel = new JBLabel();
    myChangesBrowser = new MyChangesBrowser(project, emptyList());

    HyperlinkLabel swapSidesLabel = new HyperlinkLabel("Swap branches");
    swapSidesLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        boolean swapSides = myVcsSettings.shouldSwapSidesInCompareBranches();
        myVcsSettings.setSwapSidesInCompareBranches(!swapSides);
        refreshView();
      }
    });

    JPanel topPanel = new JPanel(new HorizontalLayout(JBUI.scale(10)));
    topPanel.add(myLabel);
    topPanel.add(swapSidesLabel);

    setLayout(new BorderLayout(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP));
    add(topPanel, BorderLayout.NORTH);
    add(myChangesBrowser);

    refreshView();
  }

  private void refreshView() {
    boolean swapSides = myVcsSettings.shouldSwapSidesInCompareBranches();

    String currentBranchText = String.format("current working tree on <b><code>%s</code></b>", myCurrentBranchName);
    String otherBranchText = String.format("files in <b><code>%s</code></b>", myBranchName);
    myLabel.setText(String.format("<html>Difference between %s and %s:</html>",
                                  swapSides ? otherBranchText : currentBranchText,
                                  swapSides ? currentBranchText : otherBranchText));

    List<Change> diff = myCompareInfo.getTotalDiff();
    if (swapSides) diff = swapRevisions(diff);
    myChangesBrowser.setChangesToDisplay(diff);
  }

  @NotNull
  private static List<Change> swapRevisions(@NotNull List<Change> changes) {
    return ContainerUtil.map(changes, change -> new Change(change.getAfterRevision(), change.getBeforeRevision()));
  }

  private static class MyChangesBrowser extends SimpleChangesBrowser {
    public MyChangesBrowser(@NotNull Project project, @NotNull List<Change> changes) {
      super(project, false, true);
      setChangesToDisplay(changes);
    }

    @Override
    public void setChangesToDisplay(@NotNull Collection<? extends Change> changes) {
      List<Change> oldSelection = getSelectedChanges();
      super.setChangesToDisplay(changes);
      myViewer.setSelectedChanges(swapRevisions(oldSelection));
    }
  }
}

