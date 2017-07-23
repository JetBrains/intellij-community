/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import git4idea.config.GitVcsSettings;
import git4idea.util.GitCommitCompareInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * @author Kirill Likhodedov
 */
class GitCompareBranchesDiffPanel extends JPanel {

  private final String myBranchName;
  private final String myCurrentBranchName;
  private final GitCommitCompareInfo myCompareInfo;
  private final GitVcsSettings myVcsSettings;

  private final JBLabel myLabel;
  private final MyChangesBrowser myChangesBrowser;

  public GitCompareBranchesDiffPanel(Project project, String branchName, String currentBranchName, GitCommitCompareInfo compareInfo) {
    myCurrentBranchName = currentBranchName;
    myCompareInfo = compareInfo;
    myBranchName = branchName;
    myVcsSettings = GitVcsSettings.getInstance(project);

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

  private static class MyChangesBrowser extends ChangesBrowser {
    public MyChangesBrowser(@NotNull Project project, @NotNull List<Change> changes) {
      super(project, null, changes, null, false, true, null, ChangesBrowser.MyUseCase.COMMITTED_CHANGES, null);
    }

    @Override
    public void setChangesToDisplay(final List<Change> changes) {
      List<Change> oldSelection = myViewer.getSelectedChanges();
      super.setChangesToDisplay(changes);
      myViewer.select(swapRevisions(oldSelection));
    }
  }
}
