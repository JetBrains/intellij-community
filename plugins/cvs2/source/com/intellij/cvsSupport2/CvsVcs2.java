package com.intellij.cvsSupport2;


import com.intellij.cvsSupport2.actions.EditAction;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextAdapter;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.application.CvsStorageComponent;
import com.intellij.cvsSupport2.application.CvsStorageSupportingDeletionComponent;
import com.intellij.cvsSupport2.checkinProject.CvsCheckinEnvironment;
import com.intellij.cvsSupport2.checkinProject.CvsCheckinFile;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperation;
import com.intellij.cvsSupport2.cvsstatuses.CvsEntriesListener;
import com.intellij.cvsSupport2.cvsstatuses.CvsStatusProvider;
import com.intellij.cvsSupport2.cvsstatuses.CvsUpToDateRevisionProvider;
import com.intellij.cvsSupport2.fileView.CvsFileViewEnvironment;
import com.intellij.cvsSupport2.history.CvsHistoryProvider;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.fileView.FileViewEnvironment;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * This class intended to be an adapter of  AbstractVcs and ProjectComponent interfaces for CVS
 *
 * @author pavel
 * @author lesya
 */

public class CvsVcs2 extends AbstractVcs implements ProjectComponent,
                                                    TransactionProvider, EditFileProvider,
                                                    CvsEntriesListener {

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

  public CvsVcs2(Project project, CvsStorageComponent cvsStorageComponent) {
    super(project);
    myCvsHistoryProvider = new CvsHistoryProvider(project);
    myCvsCheckinEnvironment = new CvsCheckinEnvironment(getProject());
    myCvsStandardOperationsProvider = new CvsStandardOperationsProvider(project);
    myCvsUpdateEnvironment = new CvsUpdateEnvironment(project);
    myCvsStatusEnvironment = new CvsStatusEnvironment(myProject);
    myUpToDateRevisionProvider = new CvsUpToDateRevisionProvider(myProject);

    myConfigurable = new Cvs2Configurable(getProject());
    myStorageComponent = cvsStorageComponent;
    myFileViewEnvironment = new CvsFileViewEnvironment(getProject());
  }

  /* ======================================= ProjectComponent */

  public void projectClosed() {
    myProjectIsOpened = false;
  }

  public void projectOpened() {
    myProjectIsOpened = true;
  }

  public String getComponentName() {
    return "CvsVcs2";
  }

  public void initComponent() { }

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
    return "CVS";
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

  private static class MyFileStatusProvider implements FileStatusProvider {
    public FileStatus getStatus(VirtualFile virtualFile) {
      return CvsStatusProvider.getStatus(virtualFile);
    }
  }

  public EditFileProvider getEditFileProvider() {
    return this;
  }

  public void editFiles(final VirtualFile[] files) {
    new EditAction().actionPerformed(new CvsContextAdapter() {
      public Project getProject() {
        return myProject;
      }

      public VirtualFile[] getSelectedFiles() {
        return files;
      }
    });
  }

  public String getRequestText() {
    return "Would you like to invoke 'CVS Edit' command?";
  }

  public UpToDateRevisionProvider getUpToDateRevisionProvider() {
    return myUpToDateRevisionProvider;
  }

  public CvsOperation getTransactionForOperations(CvsCheckinFile[] operations, String message) throws VcsException {
    return myCvsStandardOperationsProvider.getTransactionForOperation(operations, message);
  }

  public void start() throws VcsException {
    super.start();
    myStorageComponent.init(getProject(), false);
    CvsEntriesManager.getInstance().addCvsEntriesListener(this);
    FileStatusManager.getInstance(getProject()).fileStatusesChanged();
  }

  public void shutdown() throws VcsException {
    super.shutdown();
    myStorageComponent.dispose();
    CvsEntriesManager.getInstance().removeCvsEntriesListener(this);
    if (myProjectIsOpened) {
      FileStatusManager.getInstance(getProject()).fileStatusesChanged();
    }
  }

  public void entriesChanged(VirtualFile parent) {
    VirtualFile[] children = parent.getChildren();
    if (children == null) return;
    for (int i = 0; i < children.length; i++) {
      entryChanged(children[i]);
    }
  }

  public void entryChanged(VirtualFile file) {
    FileStatusManager.getInstance(getProject()).fileStatusChanged(file);
  }

  public FileViewEnvironment getFileViewEnvironment() {
    return myFileViewEnvironment;
  }

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
    return "&CVS";
  }

  public UpdateEnvironment getUpdateEnvironment() {
    CvsConfiguration.getInstance(myProject).CLEAN_COPY = false;
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
}

