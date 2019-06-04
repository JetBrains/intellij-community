// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.provider.commit;

import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.vcs.commit.AmendCommitAware;
import com.intellij.vcs.commit.AmendCommitHandler;
import com.intellij.vcs.commit.AmendCommitModeListener;
import com.intellij.vcs.commit.ToggleAmendCommitOption;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.action.HgActionUtil;
import org.zmlx.hg4idea.command.*;
import org.zmlx.hg4idea.command.mq.HgQNewCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.provider.HgCurrentBinaryContentRevision;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.vcs.commit.AbstractCommitWorkflowKt.isAmendCommitMode;
import static com.intellij.vcs.commit.ToggleAmendCommitOption.isAmendCommitOptionSupported;
import static org.zmlx.hg4idea.provider.commit.HgCommitAndPushExecutorKt.isPushAfterCommit;
import static org.zmlx.hg4idea.util.HgUtil.getRepositoryManager;

public class HgCheckinEnvironment implements CheckinEnvironment, AmendCommitAware {
  @NotNull private final HgVcs myVcs;
  @NotNull private final Project myProject;
  private boolean myShouldCommitSubrepos;
  private boolean myMqNewPatch;
  private boolean myCloseBranch;
  @Nullable private Collection<HgRepository> myRepos;

  public HgCheckinEnvironment(@NotNull HgVcs vcs) {
    myVcs = vcs;
    myProject = vcs.getProject();
  }

  @Nullable
  @Override
  public RefreshableOnComponent createCommitOptions(@NotNull CheckinProjectPanel commitPanel, @NotNull CommitContext commitContext) {
    reset();

    Collection<HgRepository> repos = HgActionUtil.collectRepositoriesFromFiles(getRepositoryManager(myProject), commitPanel.getRoots());
    boolean hasSubrepos = ContainerUtil.exists(repos, HgRepository::hasSubrepos);
    boolean showAmendOption = isAmendCommitOptionSupported(commitPanel, this);

    if (!hasSubrepos && !showAmendOption) return null;

    return new HgCommitAdditionalComponent(commitPanel, hasSubrepos, showAmendOption);
  }

  private void reset() {
    myShouldCommitSubrepos = false;
    myCloseBranch = false;
    myMqNewPatch = false;
    myRepos = null;
  }

  @Override
  public String getHelpId() {
    return null;
  }

  @Override
  public String getCheckinOperationName() {
    return HgVcsMessages.message("hg4idea.commit");
  }

  @Override
  public boolean isAmendCommitSupported() {
    return myVcs.getVersion().isAmendSupported();
  }

  @Nullable
  @Override
  public String getLastCommitMessage(@NotNull VirtualFile root) {
    HgCommandExecutor commandExecutor = new HgCommandExecutor(myProject);
    List<String> args = new ArrayList<>();
    args.add("-r");
    args.add(".");
    args.add("--template");
    args.add("{desc}");
    HgCommandResult result = commandExecutor.executeInCurrentThread(root, "log", args);
    return result == null ? "" : result.getRawOutput();
  }

  @NotNull
  @Override
  public List<VcsException> commit(@NotNull List<Change> changes,
                                   @NotNull String commitMessage,
                                   @NotNull CommitContext commitContext,
                                   @NotNull Set<String> feedback) {
    List<VcsException> exceptions = new LinkedList<>();
    Map<HgRepository, Set<HgFile>> repositoriesMap = getFilesByRepository(changes);
    addRepositoriesWithoutChanges(repositoriesMap);
    boolean isAmend = isAmendCommitMode(commitContext);
    for (Map.Entry<HgRepository, Set<HgFile>> entry : repositoriesMap.entrySet()) {

      HgRepository repo = entry.getKey();
      Set<HgFile> selectedFiles = entry.getValue();
      HgCommitTypeCommand command =
        myMqNewPatch
        ? new HgQNewCommand(myProject, repo, commitMessage, isAmend)
        : new HgCommitCommand(myProject, repo, commitMessage, isAmend, myCloseBranch, myShouldCommitSubrepos && !selectedFiles.isEmpty());

      if (isMergeCommit(repo.getRoot())) {
        //partial commits are not allowed during merges
        //verifyResult that all changed files in the repo are selected
        //If so, commit the entire repository
        //If not, abort

        Set<HgFile> changedFilesNotInCommit = getChangedFilesNotInCommit(repo.getRoot(), selectedFiles);
        boolean partial = !changedFilesNotInCommit.isEmpty();


        if (partial) {
          final StringBuilder filesNotIncludedString = new StringBuilder();
          for (HgFile hgFile : changedFilesNotInCommit) {
            filesNotIncludedString.append("<li>");
            filesNotIncludedString.append(hgFile.getRelativePath());
            filesNotIncludedString.append("</li>");
          }
          if (!mayCommitEverything(filesNotIncludedString.toString())) {
            //abort
            return exceptions;
          }
          //firstly selected changes marked dirty in SingleChangeListCommitter -> doPostRefresh, so we need to mark others
          VcsDirtyScopeManager dirtyManager = VcsDirtyScopeManager.getInstance(myProject);
          for (HgFile hgFile : changedFilesNotInCommit) {
            dirtyManager.fileDirty(hgFile.toFilePath());
          }
        }
        // else : all was included, or it was OK to commit everything,
        // so no need to set the files on the command, because then mercurial will complain
      }
      else {
        command.setFiles(selectedFiles);
      }
      try {
        command.executeInCurrentThread();
      }
      catch (HgCommandException e) {
        exceptions.add(new VcsException(e));
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }

    // push if needed
    if (isPushAfterCommit(commitContext) && exceptions.isEmpty()) {
      final List<HgRepository> preselectedRepositories = new ArrayList<>(repositoriesMap.keySet());
      GuiUtils.invokeLaterIfNeeded(() ->
                                     new VcsPushDialog(myProject, preselectedRepositories, HgUtil.getCurrentRepository(myProject)).show(),
                                   ModalityState.defaultModalityState());
    }

    return exceptions;
  }

  private boolean isMergeCommit(VirtualFile repo) {
    return new HgWorkingCopyRevisionsCommand(myProject).parents(repo).size() > 1;
  }

  private Set<HgFile> getChangedFilesNotInCommit(VirtualFile repo, Set<HgFile> selectedFiles) {
    List<HgRevisionNumber> parents = new HgWorkingCopyRevisionsCommand(myProject).parents(repo);

    HgStatusCommand statusCommand =
      new HgStatusCommand.Builder(true).unknown(false).ignored(false).baseRevision(parents.get(0)).build(myProject);
    Set<HgChange> allChangedFilesInRepo = statusCommand.executeInCurrentThread(repo);

    Set<HgFile> filesNotIncluded = new HashSet<>();

    for (HgChange change : allChangedFilesInRepo) {
      HgFile beforeFile = change.beforeFile();
      HgFile afterFile = change.afterFile();
      if (!selectedFiles.contains(beforeFile)) {
        filesNotIncluded.add(beforeFile);
      }
      else if (!selectedFiles.contains(afterFile)) {
        filesNotIncluded.add(afterFile);
      }
    }
    return filesNotIncluded;
  }

  private boolean mayCommitEverything(final String filesNotIncludedString) {
    final int[] choice = new int[1];
    Runnable runnable = () -> choice[0] = Messages.showOkCancelDialog(
      myProject,
      HgVcsMessages.message("hg4idea.commit.partial.merge.message", filesNotIncludedString),
      HgVcsMessages.message("hg4idea.commit.partial.merge.title"),
      null
    );
    ApplicationManager.getApplication().invokeAndWait(runnable);
    return choice[0] == Messages.OK;
  }

  @Override
  public List<VcsException> scheduleMissingFileForDeletion(@NotNull List<FilePath> files) {
    final List<HgFile> filesWithRoots = new ArrayList<>();
    for (FilePath filePath : files) {
      VirtualFile vcsRoot = VcsUtil.getVcsRootFor(myProject, filePath);
      if (vcsRoot == null) {
        continue;
      }
      filesWithRoots.add(new HgFile(vcsRoot, filePath));
    }
    new Task.Backgroundable(myProject, "Removing Files...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        new HgRemoveCommand(myProject).executeInCurrentThread(filesWithRoots);
      }
    }.queue();
    return null;
  }

  @Override
  public List<VcsException> scheduleUnversionedFilesForAddition(@NotNull final List<VirtualFile> files) {
    new HgAddCommand(myProject).addWithProgress(files);
    return null;
  }

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return false;
  }

  @NotNull
  private Map<HgRepository, Set<HgFile>> getFilesByRepository(List<Change> changes) {
    Map<HgRepository, Set<HgFile>> result = new HashMap<>();
    for (Change change : changes) {
      ContentRevision afterRevision = change.getAfterRevision();
      ContentRevision beforeRevision = change.getBeforeRevision();

      if (afterRevision != null) {
        addFile(result, afterRevision);
      }
      if (beforeRevision != null) {
        addFile(result, beforeRevision);
      }
    }
    return result;
  }

  private void addFile(Map<HgRepository, Set<HgFile>> result, ContentRevision contentRevision) {
    FilePath filePath = contentRevision.getFile();
    // try to find repository from hgFile from change: to be able commit sub repositories as expected
    HgRepository repo = HgUtil.getRepositoryForFile(myProject, contentRevision instanceof HgCurrentBinaryContentRevision
                                                               ? ((HgCurrentBinaryContentRevision)contentRevision).getRepositoryRoot()
                                                               : ChangesUtil.findValidParentAccurately(filePath));
    if (repo == null) {
      return;
    }

    Set<HgFile> hgFiles = result.get(repo);
    if (hgFiles == null) {
      hgFiles = new HashSet<>();
      result.put(repo, hgFiles);
    }

    hgFiles.add(new HgFile(repo.getRoot(), filePath));
  }

  public void setMqNew() {
    myMqNewPatch = true;
  }

  public void setCloseBranch(boolean closeBranch) {
    myCloseBranch = closeBranch;
  }

  public void setRepos(@NotNull Collection<HgRepository> repos) {
    myRepos = repos;
  }

  private void addRepositoriesWithoutChanges(@NotNull Map<HgRepository, Set<HgFile>> repositoryMap) {
    if (myRepos == null) return;
    for (HgRepository repository : myRepos) {
      if (!repositoryMap.keySet().contains(repository)) {
        repositoryMap.put(repository, Collections.emptySet());
      }
    }
  }

  /**
   * Commit options for hg
   */
  public class HgCommitAdditionalComponent implements RefreshableOnComponent, AmendCommitModeListener, Disposable {
    @NotNull private final JPanel myPanel;
    @NotNull private final JCheckBox myCommitSubrepos;
    @NotNull private final CheckinProjectPanel myCommitPanel;
    @Nullable private final ToggleAmendCommitOption myAmendOption;

    HgCommitAdditionalComponent(@NotNull CheckinProjectPanel panel, boolean hasSubrepos, boolean showAmendOption) {
      myCommitPanel = panel;
      myAmendOption = showAmendOption ? new ToggleAmendCommitOption(myCommitPanel, this) : null;

      myCommitSubrepos = new JCheckBox("Commit subrepositories", false);
      myCommitSubrepos.setVisible(hasSubrepos);
      myCommitSubrepos.setToolTipText(XmlStringUtil.wrapInHtml(
        "Commit all subrepos for selected repositories.<br>" +
        " <code>hg ci <i><b>files</b></i> -S <i><b>subrepos</b></i></code>"));
      myCommitSubrepos.setMnemonic('s');
      myCommitSubrepos.addActionListener(e -> updateAmendState(!myCommitSubrepos.isSelected()));

      GridBag gb = new GridBag().
        setDefaultInsets(JBUI.insets(2)).
        setDefaultAnchor(GridBagConstraints.WEST).
        setDefaultWeightX(1).
        setDefaultFill(GridBagConstraints.HORIZONTAL);
      myPanel = new JPanel(new GridBagLayout());
      if (myAmendOption != null) myPanel.add(myAmendOption, gb.nextLine().next());
      myPanel.add(myCommitSubrepos, gb.nextLine().next());

      getAmendHandler().addAmendCommitModeListener(this, this);
    }

    @NotNull
    private AmendCommitHandler getAmendHandler() {
      return myCommitPanel.getCommitWorkflowHandler().getAmendCommitHandler();
    }

    @Override
    public void dispose() {
    }

    @Override
    public void amendCommitModeToggled() {
      updateCommitSubreposState();
    }

    @Override
    public void refresh() {
      myShouldCommitSubrepos = false;
    }

    @Override
    public void saveState() {
      myShouldCommitSubrepos = myCommitSubrepos.isSelected();
    }

    @Override
    public void restoreState() {
      updateCommitSubreposState();
      refresh();
    }

    @Override
    public JComponent getComponent() {
      return myPanel;
    }

    public boolean isAmend() {
      return getAmendHandler().isAmendCommitMode();
    }

    private void updateCommitSubreposState() {
      boolean isAmendMode = isAmend();

      myCommitSubrepos.setEnabled(!isAmendMode);
      if (isAmendMode) myCommitSubrepos.setSelected(false);
    }

    private void updateAmendState(boolean enable) {
      getAmendHandler().setAmendCommitModeTogglingEnabled(enable);
      if (myAmendOption != null) myAmendOption.setEnabled(enable);
      if (!enable) getAmendHandler().setAmendCommitMode(false);
    }
  }
}
