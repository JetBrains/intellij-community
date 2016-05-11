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

import com.intellij.dvcs.DvcsCommitAdditionalComponent;
import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FunctionUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class HgCheckinEnvironment implements CheckinEnvironment {

  private final Project myProject;
  private boolean myNextCommitIsPushed;
  private boolean myNextCommitAmend; // If true, the next commit is amended
  private boolean myShouldCommitSubrepos;
  private boolean myMqNewPatch;
  private boolean myCloseBranch;
  @Nullable private Collection<HgRepository> myRepos;

  public HgCheckinEnvironment(Project project) {
    myProject = project;
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel,
                                                             PairConsumer<Object, Object> additionalDataConsumer) {
    reset();
    return new HgCommitAdditionalComponent(myProject, panel);
  }

  private void reset() {
    myNextCommitIsPushed = false;
    myShouldCommitSubrepos = false;
    myCloseBranch = false;
    myMqNewPatch = false;
    myRepos = null;
  }

  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    return null;
  }

  public String getHelpId() {
    return null;
  }

  public String getCheckinOperationName() {
    return HgVcsMessages.message("hg4idea.commit");
  }

  public List<VcsException> commit(List<Change> changes,
                                   String preparedComment,
                                   @NotNull NullableFunction<Object, Object> parametersHolder,
                                   Set<String> feedback) {
    List<VcsException> exceptions = new LinkedList<VcsException>();
    Map<HgRepository, Set<HgFile>> repositoriesMap = getFilesByRepository(changes);
    addRepositoriesWithoutChanges(repositoriesMap);
    for (Map.Entry<HgRepository, Set<HgFile>> entry : repositoriesMap.entrySet()) {

      HgRepository repo = entry.getKey();
      Set<HgFile> selectedFiles = entry.getValue();
      HgCommitTypeCommand command = myMqNewPatch ? new HgQNewCommand(myProject, repo, preparedComment, myNextCommitAmend) :
                                    new HgCommitCommand(myProject, repo, preparedComment, myNextCommitAmend, myCloseBranch,
                                                        myShouldCommitSubrepos && !selectedFiles.isEmpty());

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
          //firstly selected changes marked dirty in CommitHelper -> postRefresh, so we need to mark others
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
    if (myNextCommitIsPushed && exceptions.isEmpty()) {
      final List<HgRepository> preselectedRepositories = ContainerUtil.newArrayList(repositoriesMap.keySet());
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        public void run() {
          new VcsPushDialog(myProject, preselectedRepositories, HgUtil.getCurrentRepository(myProject)).show();
        }
      });
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

    Set<HgFile> filesNotIncluded = new HashSet<HgFile>();

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
    Runnable runnable = new Runnable() {
      public void run() {
        choice[0] = Messages.showOkCancelDialog(
          myProject,
          HgVcsMessages.message("hg4idea.commit.partial.merge.message", filesNotIncludedString),
          HgVcsMessages.message("hg4idea.commit.partial.merge.title"),
          null
        );
      }
    };
    ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.defaultModalityState());
    return choice[0] == Messages.OK;
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    return commit(changes, preparedComment, FunctionUtil.nullConstant(), null);
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
    final List<HgFile> filesWithRoots = new ArrayList<HgFile>();
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

  public List<VcsException> scheduleUnversionedFilesForAddition(final List<VirtualFile> files) {
    new HgAddCommand(myProject).addWithProgress(files);
    return null;
  }

  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return false;
  }

  @NotNull
  private Map<HgRepository, Set<HgFile>> getFilesByRepository(List<Change> changes) {
    Map<HgRepository, Set<HgFile>> result = new HashMap<HgRepository, Set<HgFile>>();
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
      hgFiles = new HashSet<HgFile>();
      result.put(repo, hgFiles);
    }

    hgFiles.add(new HgFile(repo.getRoot(), filePath));
  }

  public void setNextCommitIsPushed() {
    myNextCommitIsPushed = true;
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
        repositoryMap.put(repository, Collections.<HgFile>emptySet());
      }
    }
  }

  /**
   * Commit options for hg
   */
  private class HgCommitAdditionalComponent extends DvcsCommitAdditionalComponent {
    @NotNull private final JCheckBox myCommitSubrepos;

    public HgCommitAdditionalComponent(@NotNull Project project, @NotNull CheckinProjectPanel panel) {
      super(project, panel);
      HgVcs myVcs = HgVcs.getInstance(myProject);
      myAmend.setEnabled(myVcs != null && myVcs.getVersion().isAmendSupported());
      myAmend.setText(myAmend.getText() + " (QRefresh)");
      final Insets insets = JBUI.insets(2);
      // add commit subrepos checkbox
      GridBagConstraints c = new GridBagConstraints();
      c.anchor = GridBagConstraints.CENTER;
      c.insets = insets;
      c.gridx = 1;
      c.gridy = 2;
      c.weightx = 1;
      c.fill = GridBagConstraints.HORIZONTAL;
      myCommitSubrepos = new JCheckBox("Commit subrepositories", false);
      myCommitSubrepos.setToolTipText(XmlStringUtil.wrapInHtml(
        "Commit all subrepos for selected repositories.<br>" +
        " <code>hg ci <i><b>files</b></i> -S <i><b>subrepos</b></i></code>"));
      myCommitSubrepos.setMnemonic('s');
      myPanel.add(myCommitSubrepos, c);
      Collection<HgRepository> repos =
        HgActionUtil.collectRepositoriesFromFiles(HgUtil.getRepositoryManager(myProject), myCheckinPanel.getRoots());
      myCommitSubrepos.setVisible(ContainerUtil.exists(repos, new Condition<HgRepository>() {
        @Override
        public boolean value(HgRepository repository) {
          return repository.hasSubrepos();
        }
      }));
      myCommitSubrepos.addActionListener(new MySelectionListener(myAmend));
      myAmend.addActionListener(new MySelectionListener(myCommitSubrepos));
    }

    @Override
    public void refresh() {
      super.refresh();
      restoreState();
    }

    @Override
    public void saveState() {
      myNextCommitAmend = myAmend.isSelected();
      myShouldCommitSubrepos = myCommitSubrepos.isSelected();
    }

    @Override
    public void restoreState() {
      myNextCommitAmend = false;
      myShouldCommitSubrepos = false;
    }

    @NotNull
    @Override
    protected Set<VirtualFile> getVcsRoots(@NotNull Collection<FilePath> filePaths) {
      return HgUtil.hgRoots(myProject, filePaths);
    }

    @Nullable
    @Override
    protected String getLastCommitMessage(@NotNull VirtualFile repo) throws VcsException {
      HgCommandExecutor commandExecutor = new HgCommandExecutor(myProject);
      List<String> args = new ArrayList<String>();
      args.add("-r");
      args.add(".");
      args.add("--template");
      args.add("{desc}");
      HgCommandResult result = commandExecutor.executeInCurrentThread(repo, "log", args);
      return result == null ? "" : result.getRawOutput();
    }

    private class MySelectionListener implements ActionListener {
      private final JCheckBox myUnselectedComponent;

      public MySelectionListener(JCheckBox unselectedComponent) {
        myUnselectedComponent = unselectedComponent;
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        JCheckBox source = (JCheckBox)e.getSource();
        if (source.isSelected()) {
          myUnselectedComponent.setSelected(false);
          myUnselectedComponent.setEnabled(false);
        }
        else {
          myUnselectedComponent.setEnabled(true);
        }
      }
    }
  }
}
