package com.intellij.cvsSupport2.checkinProject;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.IgnoreFileAction;
import com.intellij.cvsSupport2.actions.RemoveLocallyDeletedFilesAction;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvshandlers.CheckinProject;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvshandlers.FileSetToBeUpdated;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.DifferenceType;
import com.intellij.openapi.vcs.checkin.RevisionsFactory;
import com.intellij.openapi.vcs.checkin.VcsOperation;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.ColumnInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * author: lesya
 */
public class CvsCheckinEnvironment implements CheckinEnvironment {

  private final Project myProject;


  public CvsCheckinEnvironment(Project project) {
    myProject = project;
  }

  public RevisionsFactory getRevisionsFactory() {
    return new CvsRevisionsFactory(myProject);
  }


  public DifferencesProvider createDifferencesProviderOn(Project project, VirtualFile virtualFile) {
    return new CvsDifferencesProvider(project, virtualFile);
  }

  public RollbackProvider createRollbackProviderOn(DataContext provider) {
    return new CvsRollbackProvider(provider);
  }

  public DifferenceType[] getAdditionalDifferenceTypes() {
    return new DifferenceType[]{CvsBasedRevisions.UNKNOWN,
                                CvsBasedFileRevisions.DELETED_FROM_FS};
  }

  public ColumnInfo[] getAdditionalColumns(int index) {
    return new ColumnInfo[0];
  }

  public RefreshableOnComponent createAdditionalOptionsPanelForCheckinProject(final Refreshable panel) {
    return new CvsProjectAdditionalPanel(panel, myProject);
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(Refreshable panel, final boolean checkinProject) {
    return new AdditionalOptionsPanel(checkinProject, CvsConfiguration.getInstance(myProject));
  }

  public RefreshableOnComponent createAdditionalOptionsPanelForCheckinFile(Refreshable panel) {
    return null;
  }

  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    if (filesToCheckin == null) {
      return null;
    }
    if (filesToCheckin.length != 1) {
      return null;
    }
    return CvsUtil.getTemplateFor(filesToCheckin[0]);
  }

  public void onRefreshFinished() {
    CvsEntriesManager.getInstance().unlockSynchronizationActions();
  }

  public void onRefreshStarted() {
    CvsEntriesManager.getInstance().lockSynchronizationActions();
  }

  public AnAction[] getAdditionalActions(int index) {
    AddUnknownFileToCvsAction addAction = new AddUnknownFileToCvsAction();
    IgnoreFileAction ignoreAction = new IgnoreFileAction();
    RemoveLocallyDeletedFilesAction removeAction = new RemoveLocallyDeletedFilesAction();

    addAction.getTemplatePresentation().setIcon(IconLoader.getIcon("/actions/submit2.png"));
    removeAction.getTemplatePresentation().setIcon(IconLoader.getIcon("/actions/submit2.png"));
    ignoreAction.getTemplatePresentation().setIcon(IconLoader.getIcon("/actions/ignore2.png"));

    addAction.getTemplatePresentation().setDescription("Add to CVS...");
    removeAction.getTemplatePresentation().setDescription("Remove from CVS...");
    ignoreAction.getTemplatePresentation().setDescription("Ignore");

    if (index == 0) {
      return new AnAction[]{removeAction};
    }
    else {
      return new AnAction[]{addAction,
                            ignoreAction};
    }

  }

  public String prepareCheckinMessage(String text) {
    if (text == null) return null;
    String[] lines = LineTokenizer.tokenize(text.toCharArray(), false);
    StringBuffer buffer = new StringBuffer();
    boolean firstLine = true;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (!line.startsWith("CVS:")) {
        if (!firstLine) buffer.append(System.getProperty("line.separator"));
        buffer.append(line);
        firstLine = false;
      }
    }
    return buffer.toString();
  }

  public String getHelpId() {
    return "cvs.commitProject";
  }

  public java.util.List<VcsException> commit(CheckinProjectDialogImplementer dialog, Project project) {
    CheckinProjectPanel checkinProjectPanel = dialog.getCheckinProjectPanel();

    java.util.List<CvsCheckinFile> cvsCommitOprtations = new ArrayList<CvsCheckinFile>();

    java.util.List<VcsOperation> vcsOperations = checkinProjectPanel.getCheckinOperations(CvsVcs2.getInstance(myProject).getCheckinEnvironment());
    for (Iterator<VcsOperation> iterator = vcsOperations.iterator(); iterator.hasNext();) {
      cvsCommitOprtations.add((CvsCheckinFile)iterator.next());
    }

    CvsCheckinFile[] checkinOperations = cvsCommitOprtations.toArray(new CvsCheckinFile[cvsCommitOprtations.size()]);


    Collection<VirtualFile> files = new ArrayList<VirtualFile>();
    for (int i = 0; i < checkinOperations.length; i++) {
      CvsVcsOperation checkinOperation = (CvsVcsOperation)checkinOperations[i];
      VirtualFile file = checkinOperation.getVirtualFile();
      if (file != null) files.add(file);
    }

    CvsHandler checkinHandler = getCheckinHandler(checkinOperations,
                                                 myProject,
                                                 dialog.getPreparedComment(CvsVcs2.getInstance(myProject).getCheckinEnvironment()));

    final CvsOperationExecutor executor = new CvsOperationExecutor(project);
    executor.performActionSync(checkinHandler, new MyCvsOperationExecutorCallback(checkinHandler));
    return executor.getResult().getErrorsAndWarnings();
  }

  public List<VcsException> commit(FilePath[] roots, Project project, String preparedComment) {
    final CvsOperationExecutor executor = new CvsOperationExecutor(project);
    CvsHandler handler = CommandCvsHandler.createCommitHandler(
          roots,
          new File[]{},
          preparedComment,
          "Commit " + (roots.length > 1 ? "files" : "file"),
          CvsConfiguration.getInstance(project).MAKE_NEW_FILES_READONLY);

    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    return executor.getResult().getErrorsAndWarnings();
  }

  private class MyCvsOperationExecutorCallback implements CvsOperationExecutorCallback {
    private final CvsHandler myHandler;

    public MyCvsOperationExecutorCallback(CvsHandler handler) {
      myHandler = handler;
    }

    public void executeInProgressAfterAction(ModalityContext modalityContext) {
      FileSetToBeUpdated files = myHandler.getFiles();

      try {
        files.refreshSync();
      }
      finally {

      }
    }

    public void executionFinished(boolean successfully) {
    }


    public void executionFinishedSuccessfully() {
    }
  }

  private CvsHandler getCheckinHandler(CvsCheckinFile[] checkinOperations,
                                       final Project project,
                                       String comment) {
    try {
      CvsConfiguration cvsConfiguration = CvsConfiguration.getInstance(project);
      return new CheckinProject(checkinOperations,
                                CvsVcs2.getInstance(project),
                                comment,
                                "Commit",
                                cvsConfiguration.TAG_AFTER_PROJECT_COMMIT,
                                cvsConfiguration.TAG_AFTER_PROJECT_COMMIT_NAME);
    }
    catch (VcsException e) {
      return CvsHandler.NULL;
    }
  }

  public String getCheckinOperationName() {
    return "Commit";
  }

}
