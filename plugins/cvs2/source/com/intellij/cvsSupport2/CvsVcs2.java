package com.intellij.cvsSupport2;


import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.annotate.CvsAnnotationProvider;
import com.intellij.cvsSupport2.annotate.CvsFileAnnotation;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.application.CvsStorageComponent;
import com.intellij.cvsSupport2.changeBrowser.CvsVersionsProvider;
import com.intellij.cvsSupport2.checkinProject.AdditionalOptionsPanel;
import com.intellij.cvsSupport2.checkinProject.CvsCheckinEnvironment;
import com.intellij.cvsSupport2.checkinProject.CvsCheckinFile;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.AnnotateOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui.EditOptionsDialog;
import com.intellij.cvsSupport2.cvsstatuses.CvsChangeProvider;
import com.intellij.cvsSupport2.cvsstatuses.CvsEntriesListener;
import com.intellij.cvsSupport2.cvsstatuses.CvsStatusProvider;
import com.intellij.cvsSupport2.cvsstatuses.CvsUpToDateRevisionProvider;
import com.intellij.cvsSupport2.fileView.CvsFileViewEnvironment;
import com.intellij.cvsSupport2.history.CvsHistoryProvider;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.fileView.FileViewEnvironment;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.versionBrowser.VersionsProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * This class intended to be an adapter of  AbstractVcs and ProjectComponent interfaces for CVS
 *
 * @author pavel
 * @author lesya
 */

public class CvsVcs2 extends AbstractVcs implements ProjectComponent, TransactionProvider, EditFileProvider, CvsEntriesListener {

  private Cvs2Configurable myConfigurable;


  private CvsStorageComponent myStorageComponent = CvsStorageComponent.ABSENT_STORAGE;
  private MyFileStatusProvider myFileStatusProvider = new MyFileStatusProvider();
  private final CvsHistoryProvider myCvsHistoryProvider;
  private boolean myProjectIsOpened = false;
  private final CvsCheckinEnvironment myCvsCheckinEnvironment;
  private CvsFileViewEnvironment myFileViewEnvironment;
  private final CvsStandardOperationsProvider myCvsStandardOperationsProvider;
  private final CvsUpdateEnvironment myCvsUpdateEnvironment;
  private final CvsStatusEnvironment myCvsStatusEnvironment;
  private final CvsUpToDateRevisionProvider myUpToDateRevisionProvider;
  private final CvsAnnotationProvider myCvsAnnotationProvider;
  private final CvsDiffProvider myDiffProvider;
  private VcsShowSettingOption myAddOptions;
  private VcsShowSettingOption myRemoveOptions;
  private VcsShowSettingOption myCheckoutOptions;
  private VcsShowSettingOption myEditOption;

  private VcsShowConfirmationOption myAddConfirmation;
  private VcsShowConfirmationOption myRemoveConfirmation;

  private CvsChangeProvider myChangeProvider;

  public CvsVcs2(Project project, CvsStorageComponent cvsStorageComponent) {
    super(project);
    myCvsHistoryProvider = new CvsHistoryProvider(project);
    myCvsCheckinEnvironment = new CvsCheckinEnvironment(getProject());
    myCvsStandardOperationsProvider = new CvsStandardOperationsProvider(project);
    myCvsUpdateEnvironment = new CvsUpdateEnvironment(project);
    myCvsStatusEnvironment = new CvsStatusEnvironment(myProject);
    myUpToDateRevisionProvider = new CvsUpToDateRevisionProvider(myProject, CvsEntriesManager.getInstance());

    myConfigurable = new Cvs2Configurable(getProject());
    myStorageComponent = cvsStorageComponent;
    myFileViewEnvironment = new CvsFileViewEnvironment(getProject());
    myCvsAnnotationProvider = new CvsAnnotationProvider(myProject);
    myDiffProvider = new CvsDiffProvider(myProject);
    myChangeProvider = new CvsChangeProvider(this);
  }

  /* ======================================= ProjectComponent */

  public void projectClosed() {
    myProjectIsOpened = false;
  }

  public void projectOpened() {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myAddOptions = vcsManager.getStandardOption(VcsConfiguration.StandardOption.ADD, this);
    myRemoveOptions = vcsManager.getStandardOption(VcsConfiguration.StandardOption.ADD, this);
    myCheckoutOptions = vcsManager.getStandardOption(VcsConfiguration.StandardOption.CHECKOUT, this);
    myEditOption = vcsManager.getStandardOption(VcsConfiguration.StandardOption.EDIT, this);

    myAddConfirmation = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, this);
    myRemoveConfirmation = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, this);

    vcsManager.registerCheckinHandlerFactory(new CheckinHandlerFactory() {
      public
      @NotNull
      CheckinHandler createHandler(final CheckinProjectPanel panel) {
        return new CheckinHandler() {
          @Nullable
          public RefreshableOnComponent getAfterCheckinConfigurationPanel() {
            if (panel.getAffectedVcses().contains(CvsVcs2.this)) {
              return new AdditionalOptionsPanel(true, CvsConfiguration.getInstance(myProject));
            }
            else {
              return null;
            }
          }

        };
      }
    });

    myProjectIsOpened = true;
  }

  public void initComponent() {
  }

  public String getComponentName() {
    return "CvsVcs2";
  }

  public Project getProject() {
    return myProject;
  }

  public void disposeComponent() {

  }

  /* ======================================== AbstractVcs*/
  public String getName() {
    return "CVS";
  }

  public String getDisplayName() {
    return CvsBundle.getCvsDisplayName();
  }

  public Configurable getConfigurable() {
    return myConfigurable;
  }


  public TransactionProvider getTransactionProvider() {
    return this;
  }

  public void startTransaction(Object parameters) throws VcsException {
    myCvsStandardOperationsProvider.createTransaction();
  }

  public void commitTransaction(Object parameters) throws VcsException {
    myCvsStandardOperationsProvider.commit(parameters);
    myStorageComponent.purge();
  }

  public void rollbackTransaction(Object parameters) {
    myCvsStandardOperationsProvider.rollback();
  }


  public byte[] getFileContent(String path) throws VcsException {
    return myCvsStandardOperationsProvider.getFileContent(path);
  }

  public StandardOperationsProvider getStandardOperationsProvider() {
    return myCvsStandardOperationsProvider;
  }
  /* =========================================================*/


  public static CvsVcs2 getInstance(Project project) {
    return project.getComponent(CvsVcs2.class);
  }

  public FileStatusProvider getFileStatusProvider() {
    return myFileStatusProvider;
  }

  public int getFilesToProcessCount() {
    return myCvsStandardOperationsProvider.getFilesToProcessCount();
  }

  public static void executeOperation(String title, CvsOperation operation, final Project project) throws VcsException {
    CvsOperationExecutor executor = new CvsOperationExecutor(project);
    executor.performActionSync(new CommandCvsHandler(title, operation), CvsOperationExecutorCallback.EMPTY);
    CvsResult result = executor.getResult();
    if (!result.hasNoErrors()) {
      throw result.composeError();
    }
  }

  public static void executeQuietOperation(String title, CvsOperation operation, final Project project) {
    CvsOperationExecutor executor = new CvsOperationExecutor(false, project, ModalityState.defaultModalityState());
    executor.setIsQuietOperation(true);
    executor.performActionSync(new CommandCvsHandler(title, operation), CvsOperationExecutorCallback.EMPTY);
  }

  public VcsShowSettingOption getAddOptions() {
    return myAddOptions;
  }

  public VcsShowSettingOption getRemoveOptions() {
    return myRemoveOptions;
  }

  public VcsShowSettingOption getCheckoutOptions() {
    return myCheckoutOptions;
  }

  private static class MyFileStatusProvider implements FileStatusProvider {
    public FileStatus getStatus(VirtualFile virtualFile) {
      return CvsStatusProvider.getStatus(virtualFile);
    }
  }

  public EditFileProvider getEditFileProvider() {
    return this;
  }

  public void editFiles(final VirtualFile[] files) {
    if (getEditOptions().getValue()) {
      EditOptionsDialog editOptionsDialog = new EditOptionsDialog(myProject);
      editOptionsDialog.show();
      if (!editOptionsDialog.isOK()) return;
    }

    final CvsHandler editHandler = CommandCvsHandler.createEditHandler(files, CvsConfiguration.getInstance(myProject).RESERVED_EDIT);
    new CvsOperationExecutor(true, myProject, ModalityState.current()).performActionSync(editHandler, CvsOperationExecutorCallback.EMPTY);

  }

  public String getRequestText() {
    return CvsBundle.message("message.text.edit.file.request");
  }

  public UpToDateRevisionProvider getUpToDateRevisionProvider() {
    return myUpToDateRevisionProvider;
  }


  public ChangeProvider getChangeProvider() {
    return myChangeProvider;
  }

  public CvsOperation getTransactionForOperations(CvsCheckinFile[] operations, String message) throws VcsException {
    return myCvsStandardOperationsProvider.getTransactionForOperation(operations, message);
  }

  protected void activate() {
    super.activate();
    myStorageComponent.init(getProject(), false);
    CvsEntriesManager.getInstance().addCvsEntriesListener(this);
    VcsDirtyScopeManager.getInstance(getProject()).markEverythingDirty();
    FileStatusManager.getInstance(getProject()).fileStatusesChanged();
  }

  protected void deactivate() {
    super.deactivate();
    myStorageComponent.dispose();
    CvsEntriesManager.getInstance().removeCvsEntriesListener(this);
    if (myProjectIsOpened) {
      FileStatusManager.getInstance(getProject()).fileStatusesChanged();
      VcsDirtyScopeManager.getInstance(getProject()).markEverythingDirty();
    }
  }

  public void start() throws VcsException {
    super.start();
  }

  public void shutdown() throws VcsException {
    super.shutdown();
  }

  public void entriesChanged(VirtualFile parent) {
    VirtualFile[] children = parent.getChildren();
    if (children == null) return;
    for (VirtualFile child : children) {
      fireFileStatusChanged(child);
    }

    VcsDirtyScopeManager.getInstance(getProject()).fileDirty(parent);
  }

  public void entryChanged(VirtualFile file) {
    fireFileStatusChanged(file);
    VcsDirtyScopeManager.getInstance(getProject()).fileDirty(file);
  }

  private void fireFileStatusChanged(final VirtualFile file) {
    FileStatusManager.getInstance(getProject()).fileStatusChanged(file);
  }

  public FileViewEnvironment getFileViewEnvironment() {
    return myFileViewEnvironment;
  }

  @NotNull
  public CheckinEnvironment getCheckinEnvironment() {
    return myCvsCheckinEnvironment;
  }

  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return myCvsHistoryProvider;
  }

  public VcsHistoryProvider getVcsHistoryProvider() {
    return myCvsHistoryProvider;
  }

  public String getMenuItemText() {
    return CvsBundle.message("menu.text.cvsGroup");
  }

  public UpdateEnvironment getUpdateEnvironment() {
    return myCvsUpdateEnvironment;
  }

  public boolean fileIsUnderVcs(FilePath filePath) {
    return CvsUtil.fileIsUnderCvs(filePath.getIOFile());
  }

  public boolean fileExistsInVcs(FilePath path) {
    return CvsUtil.fileExistsInCvs(path);
  }

  public UpdateEnvironment getStatusEnvironment() {
    return myCvsStatusEnvironment;
  }

  public AnnotationProvider getAnnotationProvider() {
    return myCvsAnnotationProvider;
  }

  public FileAnnotation createAnnotation(File cvsLightweightFile, VirtualFile cvsVirtualFile, String revision, CvsEnvironment environment)
    throws VcsException {
    final AnnotateOperation annotateOperation = new AnnotateOperation(cvsLightweightFile, revision, environment);
    CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(new CommandCvsHandler(CvsBundle.getAnnotateOperationName(), annotateOperation),
                               CvsOperationExecutorCallback.EMPTY);

    if (executor.getResult().hasNoErrors()) {
      return new CvsFileAnnotation(annotateOperation.getContent(), annotateOperation.getLineAnnotations(), cvsVirtualFile);
    }
    else {
      throw executor.getResult().composeError();
    }

  }

  public DiffProvider getDiffProvider() {
    return myDiffProvider;
  }

  public VcsShowSettingOption getEditOptions() {
    return myEditOption;
  }

  public VcsShowConfirmationOption getAddConfirmation() {
    return myAddConfirmation;
  }

  public VcsShowConfirmationOption getRemoveConfirmation() {
    return myRemoveConfirmation;
  }

  @Nullable
  public RevisionSelector getRevisionSelector() {
    return new CvsRevisionSelector(myProject);
  }

  public VersionsProvider getVersionsProvider(VirtualFile root) {
    return new CvsVersionsProvider(root, myProject);
  }

}

