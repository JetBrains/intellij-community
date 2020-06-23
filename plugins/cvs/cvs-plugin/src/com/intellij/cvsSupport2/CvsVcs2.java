// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.actions.merge.CvsMergeProvider;
import com.intellij.cvsSupport2.annotate.CvsAnnotationProvider;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.application.CvsStorageSupportingDeletionComponent;
import com.intellij.cvsSupport2.changeBrowser.CvsCommittedChangesProvider;
import com.intellij.cvsSupport2.checkinProject.CvsCheckinEnvironment;
import com.intellij.cvsSupport2.checkinProject.CvsRollbackEnvironment;
import com.intellij.cvsSupport2.checkout.CvsCheckoutProvider;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperation;
import com.intellij.cvsSupport2.cvsoperations.common.FindAllRootsHelper;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui.EditOptionsDialog;
import com.intellij.cvsSupport2.cvsstatuses.CvsChangeProvider;
import com.intellij.cvsSupport2.cvsstatuses.CvsEntriesListener;
import com.intellij.cvsSupport2.history.CvsHistoryProvider;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * This class intended to be an adapter of  AbstractVcs and ProjectComponent interfaces for CVS
 *
 * @author pavel
 * @author lesya
 */
public final class CvsVcs2 extends AbstractVcs implements TransactionProvider, EditFileProvider {
  private static final String NAME = "CVS";
  private static final VcsKey ourKey = createKey(NAME);
  private final Cvs2Configurable myConfigurable;

  private final CvsHistoryProvider myCvsHistoryProvider;
  private final CvsCheckinEnvironment myCvsCheckinEnvironment;
  private final CvsCheckoutProvider myCvsCheckoutProvider;

  private RollbackEnvironment myCvsRollbackEnvironment;
  private final CvsStandardOperationsProvider myCvsStandardOperationsProvider;
  private final CvsUpdateEnvironment myCvsUpdateEnvironment;
  private final CvsStatusEnvironment myCvsStatusEnvironment;
  private final CvsAnnotationProvider myCvsAnnotationProvider;
  private final CvsDiffProvider myDiffProvider;
  private final CvsCommittedChangesProvider myCommittedChangesProvider;
  private final VcsShowSettingOption myAddOptions;
  private final VcsShowSettingOption myRemoveOptions;
  private final VcsShowSettingOption myCheckoutOptions;
  private final VcsShowSettingOption myEditOption;

  private final VcsShowConfirmationOption myAddConfirmation;
  private final VcsShowConfirmationOption myRemoveConfirmation;
  private final CvsEntriesListener myCvsEntriesListener;

  private ChangeProvider myChangeProvider;
  private MergeProvider myMergeProvider;

  public CvsVcs2(@NotNull Project project) {
    super(project, NAME);
    myCvsHistoryProvider = new CvsHistoryProvider(project);
    myCvsCheckinEnvironment = new CvsCheckinEnvironment(getProject());
    myCvsCheckoutProvider = new CvsCheckoutProvider();
    myCvsStandardOperationsProvider = new CvsStandardOperationsProvider(project);
    myCvsUpdateEnvironment = new CvsUpdateEnvironment(project);
    myCvsStatusEnvironment = new CvsStatusEnvironment(myProject);

    myConfigurable = new Cvs2Configurable(getProject());
    myCvsAnnotationProvider = new CvsAnnotationProvider(myProject, myCvsHistoryProvider);
    myDiffProvider = new CvsDiffProvider(myProject);
    myCommittedChangesProvider = new CvsCommittedChangesProvider(myProject);

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myAddOptions = vcsManager.getStandardOption(VcsConfiguration.StandardOption.ADD, this);
    myRemoveOptions = vcsManager.getStandardOption(VcsConfiguration.StandardOption.ADD, this);
    myCheckoutOptions = vcsManager.getStandardOption(VcsConfiguration.StandardOption.CHECKOUT, this);
    myEditOption = vcsManager.getStandardOption(VcsConfiguration.StandardOption.EDIT, this);

    myAddConfirmation = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, this);
    myRemoveConfirmation = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, this);
    myCvsEntriesListener = new CvsEntriesListener() {

      @Override
      public void entriesChanged(VirtualFile parent) {
        VirtualFile[] children = parent.getChildren();
        if (children == null) return;
        for (VirtualFile child : children) {
          fireFileStatusChanged(child);
        }

        VcsDirtyScopeManager.getInstance(getProject()).fileDirty(parent);
      }

      @Override
      public void entryChanged(VirtualFile file) {
        if (myProject.isDisposed()) return; // invoke later is possible
        fireFileStatusChanged(file);
        VcsDirtyScopeManager.getInstance(getProject()).fileDirty(file);
      }
    };
  }

  /* ======================================= ProjectComponent */


  /* ======================================== AbstractVcs*/

  @Override
  @NotNull
  public String getDisplayName() {
    return CvsBundle.getCvsDisplayName();
  }

  @Override
  public Configurable getConfigurable() {
    return myConfigurable;
  }


  @Override
  public TransactionProvider getTransactionProvider() {
    return this;
  }

  @Override
  public void startTransaction(Object parameters) {
    myCvsStandardOperationsProvider.createTransaction();
  }

  @Override
  public void commitTransaction(Object parameters) throws VcsException {
    myCvsStandardOperationsProvider.commit(parameters);
  }

  @Override
  public void rollbackTransaction(Object parameters) {
    myCvsStandardOperationsProvider.rollback();
  }

  public CvsStandardOperationsProvider getStandardOperationsProvider() {
    return myCvsStandardOperationsProvider;
  }

  /* =========================================================*/

  public static CvsVcs2 getInstance(Project project) {
    return (CvsVcs2) ProjectLevelVcsManager.getInstance(project).findVcsByName(NAME);
  }

  public int getFilesToProcessCount() {
    return myCvsStandardOperationsProvider.getFilesToProcessCount();
  }

  public static void executeOperation(String title, CvsOperation operation, final Project project) throws VcsException {
    CvsOperationExecutor executor = new CvsOperationExecutor(project);
    executor.performActionSync(new CommandCvsHandler(title, operation), CvsOperationExecutorCallback.EMPTY);
    CvsResult result = executor.getResult();
    if (result.hasErrors()) {
      throw result.composeError();
    }
  }

  public static CvsOperationExecutor executeQuietOperation(String title, CvsOperation operation, final Project project) {
    CvsOperationExecutor executor = new CvsOperationExecutor(false, project, ModalityState.defaultModalityState());
    executor.setIsQuietOperation(true);
    executor.performActionSync(new CommandCvsHandler(title, operation), CvsOperationExecutorCallback.EMPTY);
    return executor;
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

  @Override
  public EditFileProvider getEditFileProvider() {
    return this;
  }

  @Override
  public void editFiles(final VirtualFile[] files) {
    if (getEditOptions().getValue()) {
      EditOptionsDialog editOptionsDialog = new EditOptionsDialog(myProject);
      if (!editOptionsDialog.showAndGet()) {
        return;
      }
    }

    final CvsHandler editHandler = CommandCvsHandler.createEditHandler(files, CvsConfiguration.getInstance(myProject).RESERVED_EDIT);
    new CvsOperationExecutor(true, myProject, ModalityState.current()).performActionSync(editHandler, CvsOperationExecutorCallback.EMPTY);

  }

  @Override
  public String getRequestText() {
    return CvsBundle.message("message.text.edit.file.request");
  }

  @Override
  public ChangeProvider getChangeProvider() {
    if (myChangeProvider == null) {
      myChangeProvider = new CvsChangeProvider(this, CvsEntriesManager.getInstance());
    }
    return myChangeProvider;
  }

  @Override
  protected void activate() {
    CvsStorageSupportingDeletionComponent.getInstance(myProject).activate();
    CvsEntriesManager.getInstance().addCvsEntriesListener(myCvsEntriesListener);
  }

  @Override
  protected void deactivate() {
    CvsStorageSupportingDeletionComponent.getInstance(myProject).deactivate();
    CvsEntriesManager.getInstance().removeCvsEntriesListener(myCvsEntriesListener);
  }

  private void fireFileStatusChanged(final VirtualFile file) {
    FileStatusManager.getInstance(getProject()).fileStatusChanged(file);
  }

  @Override
  @NotNull
  public CheckinEnvironment createCheckinEnvironment() {
    return myCvsCheckinEnvironment;
  }

  @Override
  public RollbackEnvironment createRollbackEnvironment() {
    if (myCvsRollbackEnvironment == null) {
      myCvsRollbackEnvironment = new CvsRollbackEnvironment(myProject);
    }
    return myCvsRollbackEnvironment;
  }

  @Override
  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return myCvsHistoryProvider;
  }

  @Override
  @NotNull
  public VcsHistoryProvider getVcsHistoryProvider() {
    return myCvsHistoryProvider;
  }

  @Override
  public String getMenuItemText() {
    return CvsBundle.message("menu.text.cvsGroup");
  }

  @Override
  public UpdateEnvironment createUpdateEnvironment() {
    return myCvsUpdateEnvironment;
  }

  @Override
  public boolean fileIsUnderVcs(FilePath filePath) {
    return CvsUtil.fileIsUnderCvs(filePath.getIOFile());
  }

  @Override
  public boolean fileExistsInVcs(FilePath path) {
    return CvsUtil.fileExistsInCvs(path);
  }

  @Override
  public UpdateEnvironment getStatusEnvironment() {
    return myCvsStatusEnvironment;
  }

  @Override
  public AnnotationProvider getAnnotationProvider() {
    return myCvsAnnotationProvider;
  }

  public FileAnnotation createAnnotation(VirtualFile cvsVirtualFile, String revision, CvsEnvironment environment) throws VcsException {
    return myCvsAnnotationProvider.annotate(cvsVirtualFile, revision, environment);
  }

  @Override
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

  @Override
  @Nullable
  public RevisionSelector getRevisionSelector() {
    return new CvsRevisionSelector(myProject);
  }

  @NotNull
  @Override
  public CommittedChangesProvider getCommittedChangesProvider() {
    return myCommittedChangesProvider;
  }

  @Override @Nullable
  public VcsRevisionNumber parseRevisionNumber(final String revisionNumberString) {
    return new CvsRevisionNumber(revisionNumberString);
  }

  @Override
  public String getRevisionPattern() {
    return CvsUtil.REVISION_PATTERN;
  }

  @Override
  public boolean isVersionedDirectory(final VirtualFile dir) {
    final VirtualFile child = dir.findChild(NAME);
    return child != null && child.isDirectory();
  }

  @Override
  public CvsCheckoutProvider getCheckoutProvider() {
    return myCvsCheckoutProvider;
  }

  @Override
  public RootsConvertor getCustomConvertor() {
    return new RootsConvertor() {
      @Override
      @NotNull
      public List<VirtualFile> convertRoots(@NotNull List<VirtualFile> result) {
        return FindAllRootsHelper.findVersionedUnder(result);
      }
    };
  }

  @Override
  public MergeProvider getMergeProvider() {
    if (myMergeProvider != null) {
      myMergeProvider = new CvsMergeProvider();
    }
    return myMergeProvider;
  }

  public static VcsKey getKey() {
    return ourKey;
  }

  @Override
  public boolean areDirectoriesVersionedItems() {
    return true;
  }

  @NotNull
  @Override
  public <S> List<S> filterUniqueRoots(@NotNull List<S> in, @NotNull Function<? super S, ? extends VirtualFile> convertor) {
    return in;
  }
}

