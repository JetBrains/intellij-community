package com.intellij.cvsSupport2.checkinProject;

import com.intellij.CvsBundle;
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RollbackProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.DifferenceType;
import com.intellij.openapi.vcs.checkin.RevisionsFactory;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.versions.AbstractRevisions;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.ColumnInfo;

import java.io.File;
import java.util.List;

/**
 * author: lesya
 */
public class CvsCheckinEnvironment implements CheckinEnvironment {

  private final Project myProject;

  public boolean showCheckinDialogInAnyCase() {
    return false;
  }

  public CvsCheckinEnvironment(Project project) {
    myProject = project;
  }

  public RevisionsFactory getRevisionsFactory() {
    return new CvsRevisionsFactory(myProject);
  }


  public RollbackProvider createRollbackProviderOn(AbstractRevisions[] selectedRevisions, final boolean containsExcluded) {
    return new CvsRollbackProvider(myProject, selectedRevisions);
  }

  public DifferenceType[] getAdditionalDifferenceTypes() {
    return new DifferenceType[]{CvsBasedRevisions.UNKNOWN,
                                CvsBasedFileRevisions.DELETED_FROM_FS};
  }

  public ColumnInfo[] getAdditionalColumns(int index) {
    return new ColumnInfo[0];
  }

  public RefreshableOnComponent createAdditionalOptionsPanelForCheckinProject(final Refreshable panel) {
    return null;
    // TODO: shall these options be available elsewhere?
    /*return new CvsProjectAdditionalPanel(panel, myProject);*/
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(Refreshable panel, final boolean checkinProject) {
    return null;
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

    addAction.getTemplatePresentation().setDescription(com.intellij.CvsBundle.message("operation.description.add.to.cvs"));
    removeAction.getTemplatePresentation().setDescription(com.intellij.CvsBundle.message("operation.description.remove.from.cvs"));
    ignoreAction.getTemplatePresentation().setDescription(com.intellij.CvsBundle.message("operation.description.ignore"));

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
    for (String line : lines) {
      //noinspection HardCodedStringLiteral
      if (!line.startsWith("CVS:")) {
        if (!firstLine) buffer.append(SystemProperties.getLineSeparator());
        buffer.append(line);
        firstLine = false;
      }
    }
    return buffer.toString();
  }

  public String getHelpId() {
    return "cvs.commitProject";
  }

  public List<VcsException> commit(FilePath[] roots, Project project, String preparedComment) {
    final CvsOperationExecutor executor = new CvsOperationExecutor(project);
    executor.setShowErrors(false);

    final CvsConfiguration cvsConfiguration = CvsConfiguration.getInstance(myProject);

    CvsHandler handler = CommandCvsHandler.createCommitHandler(
          roots,
          new File[]{},
          preparedComment,
          CvsBundle.message("operation.name.commit.file", roots.length),
          CvsConfiguration.getInstance(project).MAKE_NEW_FILES_READONLY,
          myProject,
          cvsConfiguration.TAG_AFTER_PROJECT_COMMIT,
          cvsConfiguration.TAG_AFTER_PROJECT_COMMIT_NAME);

    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    return executor.getResult().getErrorsAndWarnings();
  }

  private class MyCvsOperationExecutorCallback implements CvsOperationExecutorCallback {
    private final CvsHandler myHandler;

    public MyCvsOperationExecutorCallback(CvsHandler handler) {
      myHandler = handler;
    }

    public void executeInProgressAfterAction(ModalityContext modalityContext) {
      myHandler.getFiles().refreshSync();
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
                                com.intellij.CvsBundle.message("operation.name.checkin.project"),
                                cvsConfiguration.TAG_AFTER_PROJECT_COMMIT,
                                cvsConfiguration.TAG_AFTER_PROJECT_COMMIT_NAME);
    }
    catch (VcsException e) {
      return CvsHandler.NULL;
    }
  }

  public String getCheckinOperationName() {
    return com.intellij.CvsBundle.message("operation.name.checkin.project");
  }

}
