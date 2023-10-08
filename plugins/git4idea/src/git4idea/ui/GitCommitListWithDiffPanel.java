// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui;

import com.intellij.dvcs.ui.CommitListPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser;
import com.intellij.util.Consumer;
import git4idea.GitCommit;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * List of commits at the left, the {@link com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase} at the right.
 * Select a commit to shows its changes in the changes browser.
 *
 * @author Kirill Likhodedov
 */
public class GitCommitListWithDiffPanel extends JPanel {

  private final SimpleAsyncChangesBrowser myChangesBrowser;
  private final CommitListPanel myCommitListPanel;

  public GitCommitListWithDiffPanel(@NotNull Project project, @NotNull List<GitCommit> commits) {
    super(new BorderLayout());

    myCommitListPanel = new CommitListPanel(commits, null);
    myCommitListPanel.addListMultipleSelectionListener(new Consumer<>() {
      @Override
      public void consume(List<Change> changes) {
        myChangesBrowser.setChangesToDisplay(changes);
      }
    });

    myChangesBrowser = new SimpleAsyncChangesBrowser(project, false, true);
    myCommitListPanel.registerDiffAction(myChangesBrowser.getDiffAction());

    Splitter splitter = new Splitter(false, 0.7f);
    splitter.setHonorComponentsMinimumSize(false);
    splitter.setFirstComponent(myCommitListPanel);
    splitter.setSecondComponent(myChangesBrowser);
    add(splitter);
  }

  public @NotNull JComponent getPreferredFocusComponent() {
    return myCommitListPanel.getPreferredFocusComponent();
  }
  
  public void setCommits(@NotNull List<GitCommit> commits) {
    myCommitListPanel.setCommits(commits);
  }

}
