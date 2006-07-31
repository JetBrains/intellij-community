package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.cvsContext.CvsLightweightFile;
import com.intellij.cvsSupport2.actions.update.UpdateSettings;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsoperations.common.CompositeOperaton;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperation;
import com.intellij.cvsSupport2.cvsoperations.common.PostCvsActivity;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddFilesOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.AnnotateOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsCheckOut.CheckoutFileByRevisionOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsCheckOut.CheckoutFileOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsCheckOut.CheckoutProjectOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsCommit.CommitFilesOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.EditOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.UneditOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsImport.ImportDetails;
import com.intellij.cvsSupport2.cvsoperations.cvsImport.ImportOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsRemove.RemoveFilesOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.BranchOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsUpdate.UpdateOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.cvsSupport2.errorHandling.CvsProcessException;
import com.intellij.cvsSupport2.errorHandling.InvalidModuleDescriptionException;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.InvalidEntryFormatException;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * author: lesya
 */
public class CommandCvsHandler extends AbstractCvsHandler {

  protected static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler");

  protected final CvsOperation myCvsOperation;

  private final List<CvsOperation> myPostActivities = new ArrayList<CvsOperation>();

  private final boolean myCanBeCanceled;
  protected boolean myIsCanceled = false;

  public boolean login(ModalityContext executor) throws CannotFindCvsRootException {
    return myCvsOperation.login(executor);
  }

  public CommandCvsHandler(String title, CvsOperation cvsOperation, boolean canBeCanceled) {
    this(title, cvsOperation, FileSetToBeUpdated.EMTPY, canBeCanceled);
  }

  public CommandCvsHandler(String title, CvsOperation cvsOperation, FileSetToBeUpdated files, boolean canBeCanceled) {
    super(title, files);
    myCvsOperation = cvsOperation;
    myCanBeCanceled = canBeCanceled;
  }

  public CommandCvsHandler(String title, CvsOperation cvsOperation, FileSetToBeUpdated files) {
    this(title, cvsOperation, files, true);
  }

  public CommandCvsHandler(String title, CvsOperation cvsOperation) {
    this(title, cvsOperation, FileSetToBeUpdated.EMTPY);
  }

  public boolean canBeCanceled() {
    return myCanBeCanceled;
  }

  @Override protected boolean runInReadThread() {
    return myCvsOperation.runInReadThread();
  }

  public static CvsHandler createCheckoutFileHandler(FilePath[] files,
                                                     CvsConfiguration configuration) {
    return new CheckoutHandler(files, configuration);
  }

  public static CvsHandler createCheckoutHandler(CvsEnvironment environment,
                                                 String[] checkoutPath,
                                                 final File target,
                                                 boolean useAltCheckoutDir,
                                                 boolean makeNewFilesReadOnly) {
    CheckoutProjectOperation checkoutOperation
      = CheckoutProjectOperation.create(environment, checkoutPath, target,
                                        useAltCheckoutDir, makeNewFilesReadOnly);

    return new CommandCvsHandler(CvsBundle.message("operation.name.check.out.project"), checkoutOperation, FileSetToBeUpdated.allFiles()) {
      public void runComplitingActivities() {
        CvsEntriesManager.getInstance().clearAll();
      }
    };
  }

  public static CvsHandler createImportHandler(ImportDetails details) {
    return new CommandCvsHandler(com.intellij.CvsBundle.message("operation.name.import"), new ImportOperation(details), FileSetToBeUpdated.EMTPY);
  }

  public static UpdateHandler createUpdateHandler(final FilePath[] files,
                                                  UpdateSettings updateSettings,
                                                  Project project, UpdatedFiles updatedFiles) {
    return new UpdateHandler(files, updateSettings, project, updatedFiles);
  }

  public static CvsHandler createBranchOrTagHandler(FilePath[] selectedFiles, String branchName,
                                                    boolean switchToThisAction, boolean overrideExisting,
                                                    boolean isTag, boolean makeNewFilesReadOnly, Project project) {
    CompositeOperaton operation = new CompositeOperaton();
    operation.addOperation(new BranchOperation(selectedFiles, branchName, overrideExisting, isTag));
    if (switchToThisAction) {
      operation.addOperation(new UpdateOperation(selectedFiles, branchName, makeNewFilesReadOnly, project));
    }
    return new CommandCvsHandler(isTag ? com.intellij.CvsBundle.message("operation.name.create.tag")
                                 : com.intellij.CvsBundle.message("operation.name.create.branch"), operation,
                                                                                                   FileSetToBeUpdated.selectedFiles(selectedFiles));
  }

  public static CvsHandler createCommitHandler(FilePath[] selectedFiles,
                                               File[] locallyDeletedFiles,
                                               String commitMessage,
                                               String title,
                                               boolean makeNewFilesReadOnly,
                                               Project project,
                                               final boolean tagFilesAfterCommit,
                                               final String tagName) {
    CommitFilesOperation operation = new CommitFilesOperation(commitMessage, makeNewFilesReadOnly);
    if (selectedFiles != null) {
      for (FilePath selectedFile : selectedFiles) {
        operation.addFile(selectedFile.getIOFile());
      }
    }
    if (locallyDeletedFiles != null) {
      for (File locallyDeletedFile : locallyDeletedFiles) {
        operation.addFile(locallyDeletedFile);
      }
    }

    final CommandCvsHandler result = new CommandCvsHandler(title, operation, FileSetToBeUpdated.selectedFiles(selectedFiles));

    if (tagFilesAfterCommit) {
      result.addOperation(new TagOperation(selectedFiles, tagName, CvsConfiguration.getInstance(project).OVERRIDE_EXISTING_TAG_FOR_PROJECT));
    }

    return result;
  }

  public static CvsHandler createAddFilesHandler(Collection addedRoots) {
    AddFilesOperation operation = new AddFilesOperation();
    ArrayList<AddedFileInfo> addedFileInfo = new ArrayList<AddedFileInfo>();
    for (final Object addedRoot : addedRoots) {
      AddedFileInfo info = (AddedFileInfo)addedRoot;
      info.clearAllCvsAdminDirectoriesInIncludedDirectories();
      addedFileInfo.addAll(info.collectAllIncludedFiles());
    }

    ArrayList<VirtualFile> addedFiles = new ArrayList<VirtualFile>();

    for (AddedFileInfo info : addedFileInfo) {
      addedFiles.add(info.getFile());
      operation.addFile(info.getFile(), info.getKeywordSubstitution());
    }
    return new CommandCvsHandler(com.intellij.CvsBundle.message("action.name.add"), operation,
                                 FileSetToBeUpdated.selectedFiles(addedFiles.toArray(new VirtualFile[addedFiles.size()])));
  }

  public static CvsHandler createRemoveFilesHandler(Collection<File> files) {
    RemoveFilesOperation operation = new RemoveFilesOperation();
    for (final File file : files) {
      operation.addFile(file.getPath());
    }
    return new CommandCvsHandler(com.intellij.CvsBundle.message("action.name.remove"), operation, FileSetToBeUpdated.selectedFiles(getAdminDirectoriesFor(files)));
  }

  private static VirtualFile[] getAdminDirectoriesFor(Collection<File> files) {
    Collection<VirtualFile> result = new HashSet<VirtualFile>();
    for (File file : files) {
      File parentFile = file.getParentFile();
      VirtualFile cvsAdminDirectory = CvsVfsUtil.findFileByIoFile(new File(parentFile, CvsUtil.CVS));
      if (cvsAdminDirectory != null) result.add(cvsAdminDirectory);
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  public static CvsHandler createRestoreFileHandler(final VirtualFile parent,
                                                    String name,
                                                    boolean makeNewFilesReadOnly) {
    final File ioFile = new File(VfsUtil.virtualToIoFile(parent), name);

    final Entry entry = CvsEntriesManager.getInstance().getEntryFor(parent, name);

    final String revision = getRevision(entry);

    final CheckoutFileByRevisionOperation operation = new CheckoutFileByRevisionOperation(parent, name, revision, makeNewFilesReadOnly);

    operation.addFinishAction(new Runnable() {
      public void run() {

        if (entry != null) {
          entry.setRevision(revision);
          entry.setConflict(CvsUtil.formatDate(new Date(ioFile.lastModified())));
          try {
            CvsUtil.saveEntryForFile(ioFile, entry);
          }
          catch (IOException e) {
            LOG.error(e);
          }
          CvsEntriesManager.getInstance().clearCachedEntriesFor(parent);
        }

      }
    });

    return new CommandCvsHandler(com.intellij.CvsBundle.message("operation.name.restore"),
                                 operation,
                                 FileSetToBeUpdated.EMTPY);
  }

  public static CvsHandler createEditHandler(VirtualFile[] selectedFiles, boolean isReservedEdit) {
    EditOperation operation = new EditOperation(isReservedEdit);
    operation.addFiles(selectedFiles);
    return new CommandCvsHandler(com.intellij.CvsBundle.message("action.name.edit"), operation, FileSetToBeUpdated.selectedFiles(selectedFiles));
  }

  public static CvsHandler createUneditHandler(VirtualFile[] selectedFiles, boolean makeNewFilesReadOnly) {
    UneditOperation operation = new UneditOperation(makeNewFilesReadOnly);
    operation.addFiles(selectedFiles);
    return new CommandCvsHandler(com.intellij.CvsBundle.message("operation.name.unedit"), operation, FileSetToBeUpdated.selectedFiles(selectedFiles));
  }

  public static CvsHandler createAnnotateHandler(AnnotateOperation operation) {
    return new CommandCvsHandler(com.intellij.CvsBundle.message("operation.name.annotate"), operation, FileSetToBeUpdated.EMTPY);
  }

  public static CvsHandler createRemoveTagAction(VirtualFile[] selectedFiles, String tagName) {
    return new CommandCvsHandler(com.intellij.CvsBundle.message("action.name.delete.tag"), new TagOperation(selectedFiles, tagName, true, false),
                                 FileSetToBeUpdated.EMTPY);
  }

  private static String getRevision(final Entry entry) {
    if (entry == null) {
      return null;
    }
    String result = entry.getRevision();
    if (result == null) return null;
    if (StringUtil.startsWithChar(result, '-')) return result.substring(1);
    return result;
  }

  public boolean isCanceled() {
    return myIsCanceled;
  }

  public void internalRun(ModalityContext executor) {
    try {
      myErrorMessageProcessor.clear();
      CvsExecutionEnvironment executionEnvironment = new CvsExecutionEnvironment(myCompositeListener,
                                                                                 getProgressListener(),
                                                                                 myErrorMessageProcessor,
                                                                                 executor,
                                                                                 getPostActivityHandler());
      myCvsOperation.execute(executionEnvironment);
      onOperationFinished(executor);


      while (!myPostActivities.isEmpty()) {
        CvsOperation cvsOperation = myPostActivities.get(0);
        if (cvsOperation.login(executor)) {
          cvsOperation.execute(executionEnvironment);
        }
        myPostActivities.remove(cvsOperation);
      }
    }
    catch (VcsException e) {
      myErrors.add(e);
    }
    catch (ProcessCanceledException e) {
      myIsCanceled = true;
    }
    catch (InvalidModuleDescriptionException ex) {
      myErrors.add(new CvsException(ex, ex.getCvsRoot()));
    }
    catch (InvalidEntryFormatException e) {
      myErrors.add(new VcsException(com.intellij.CvsBundle.message("exception.text.entries.file.is.corrupted", e.getEntriesFile())));
    }
    catch (CvsProcessException ex) {
      myErrors.add(new CvsException(ex, myCvsOperation.getLastProcessedCvsRoot()));
    }
    catch (Exception ex) {
      LOG.error(ex);
      myErrors.add(new CvsException(ex, myCvsOperation.getLastProcessedCvsRoot()));
    }

  }

  protected void onOperationFinished(ModalityContext modalityContext) {

  }


  protected void addFileToCheckout(VirtualFile file) {
    addOperation(new CheckoutFileOperation(file.getParent(), RevisionOrDateImpl.createOn(file), file.getName(), false));
  }

  protected void addOperation(CvsOperation operation) {
    myPostActivities.add(operation);
  }

  protected PostCvsActivity getPostActivityHandler() {
    return PostCvsActivity.DEAF;
  }

  protected int getFilesToProcessCount() {
    return myCvsOperation.getFilesToProcessCount();
  }

  public static CvsHandler createGetFileFromRepositoryHandler(CvsLightweightFile[] cvsLightweightFiles, boolean makeNewFilesReadOnly) {
    CompositeOperaton compositeOperaton = new CompositeOperaton();
    final CvsEntriesManager entriesManager = CvsEntriesManager.getInstance();
    for (CvsLightweightFile cvsLightweightFile : cvsLightweightFiles) {
      File root = cvsLightweightFile.getRoot();
      File workingDirectory = root;
      if (workingDirectory == null) continue;
      if (cvsLightweightFile.getLocalFile().getParentFile().equals(workingDirectory)) {
        workingDirectory = workingDirectory.getParentFile();
      }
      String alternativeCheckoutPath = getAlternativeCheckoutPath(cvsLightweightFile, workingDirectory);
      CheckoutProjectOperation checkoutFileOperation = new CheckoutProjectOperation(new String[]{cvsLightweightFile.getModuleName()},
                                                                                    entriesManager
                                                                                      .getCvsConnectionSettingsFor(root),
                                                                                    makeNewFilesReadOnly,
                                                                                    workingDirectory,
                                                                                    alternativeCheckoutPath,
                                                                                    true,
                                                                                    null);
      compositeOperaton.addOperation(checkoutFileOperation);
    }

    return new CommandCvsHandler(com.intellij.CvsBundle.message("action.name.get.file.from.repository"), compositeOperaton, FileSetToBeUpdated.allFiles(), true);

  }

  private static String getAlternativeCheckoutPath(CvsLightweightFile cvsLightweightFile, File workingDirectory) {
    File parent = cvsLightweightFile.getLocalFile().getParentFile();
    return parent.getAbsolutePath().substring(workingDirectory.getAbsolutePath().length());
  }
}
