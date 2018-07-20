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

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vcs.ui.ReplaceFileConfirmationDialog;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitCommitCompareInfo;
import git4idea.util.GitFileUtils;
import git4idea.util.GitLocalCommitCompareInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

/**
 * @author Kirill Likhodedov
 */
class GitCompareBranchesDiffPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance(GitCompareBranchesDiffPanel.class);

  private final String myBranchName;
  private final Project myProject;
  private final String myCurrentBranchName;
  private final GitCommitCompareInfo myCompareInfo;
  private final GitVcsSettings myVcsSettings;

  private final JBLabel myLabel;
  private final MyChangesBrowser myChangesBrowser;

  public GitCompareBranchesDiffPanel(Project project, String branchName, String currentBranchName, GitCommitCompareInfo compareInfo) {
    myProject = project;
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

  private class MyChangesBrowser extends SimpleChangesBrowser {
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

    @NotNull
    @Override
    protected List<AnAction> createToolbarActions() {
      return ContainerUtil.append(
        super.createToolbarActions(),
        new MyCopyChangesAction()
      );
    }

    @NotNull
    @Override
    protected List<AnAction> createPopupMenuActions() {
      return ContainerUtil.append(
        super.createPopupMenuActions(),
        new MyCopyChangesAction()
      );
    }
  }

  private class MyCopyChangesAction extends DumbAwareAction {
    public MyCopyChangesAction() {
      super("Get from Branch", "Replace file content with its version from branch " + myBranchName, AllIcons.Actions.Download);
      copyShortcutFrom(ActionManager.getInstance().getAction("Vcs.GetVersion"));
    }

    @Override
    public void update(AnActionEvent e) {
      boolean isEnabled = !myChangesBrowser.getSelectedChanges().isEmpty();
      boolean isVisible = myCompareInfo instanceof GitLocalCommitCompareInfo;
      e.getPresentation().setEnabled(isEnabled && isVisible);
      e.getPresentation().setVisible(isVisible);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      String title = String.format("Get from Branch '%s'", myBranchName);
      List<Change> changes = myChangesBrowser.getSelectedChanges();
      boolean swapSides = myVcsSettings.shouldSwapSidesInCompareBranches();

      ReplaceFileConfirmationDialog confirmationDialog = new ReplaceFileConfirmationDialog(myProject, title);
      if (!confirmationDialog.confirmFor(ChangesUtil.getFilesFromChanges(changes))) return;

      FileDocumentManager.getInstance().saveAllDocuments();
      LocalHistoryAction action = LocalHistory.getInstance().startAction(title);

      new Task.Modal(myProject, "Loading Content from Branch", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(myProject);
          MultiMap<GitRepository, FilePath> toCheckout = MultiMap.createSet();
          MultiMap<GitRepository, FilePath> toDelete = MultiMap.createSet();

          for (Change change : changes) {
            FilePath currentPath = swapSides ? ChangesUtil.getAfterPath(change) : ChangesUtil.getBeforePath(change);
            FilePath branchPath = !swapSides ? ChangesUtil.getAfterPath(change) : ChangesUtil.getBeforePath(change);
            assert currentPath != null || branchPath != null;

            GitRepository repository = repositoryManager.getRepositoryForFile(ObjectUtils.chooseNotNull(currentPath, branchPath));
            if (currentPath != null && branchPath != null) {
              if (Comparing.equal(currentPath, branchPath)) {
                toCheckout.putValue(repository, branchPath);
              }
              else {
                toDelete.putValue(repository, currentPath);
                toCheckout.putValue(repository, branchPath);
              }
            }
            else if (currentPath != null) {
              toDelete.putValue(repository, currentPath);
            }
            else {
              toCheckout.putValue(repository, branchPath);
            }
          }

          try {
            for (Map.Entry<GitRepository, Collection<FilePath>> entry : toDelete.entrySet()) {
              GitRepository repository = entry.getKey();
              Collection<FilePath> rootPaths = entry.getValue();
              VirtualFile root = repository.getRoot();

              GitFileUtils.delete(myProject, root, rootPaths);
            }

            for (Map.Entry<GitRepository, Collection<FilePath>> entry : toCheckout.entrySet()) {
              GitRepository repository = entry.getKey();
              Collection<FilePath> rootPaths = entry.getValue();
              VirtualFile root = repository.getRoot();

              for (List<String> paths : VcsFileUtil.chunkPaths(root, rootPaths)) {
                GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.CHECKOUT);
                handler.addParameters(myBranchName);
                handler.endOptions();
                handler.addParameters(paths);
                GitCommandResult result = Git.getInstance().runCommand(handler);
                result.getOutputOrThrow();
              }

              GitFileUtils.addPaths(myProject, root, rootPaths);
            }

            RefreshVFsSynchronously.updateChanges(changes);
            VcsDirtyScopeManager.getInstance(myProject).filePathsDirty(ChangesUtil.getPaths(changes), null);

            ((GitLocalCommitCompareInfo)myCompareInfo).reloadTotalDiff();
          }
          catch (VcsException err) {
            ApplicationManager.getApplication().invokeLater(() -> {
              Messages.showErrorDialog(myProject, err.getMessage(), "Can't Copy Changes");
            });
          }
        }

        @Override
        public void onFinished() {
          action.finish();

          refreshView();
        }
      }.queue();
    }
  }
}
