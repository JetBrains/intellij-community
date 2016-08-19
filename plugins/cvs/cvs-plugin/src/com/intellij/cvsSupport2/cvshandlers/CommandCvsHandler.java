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
package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.cvsContext.CvsLightweightFile;
import com.intellij.cvsSupport2.actions.update.UpdateSettings;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsoperations.common.*;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddFilesOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.cvsoperations.cvsCheckOut.CheckoutFileOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsCheckOut.CheckoutFilesOperation;
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
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.cvsSupport2.errorHandling.CvsProcessException;
import com.intellij.cvsSupport2.errorHandling.InvalidModuleDescriptionException;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.InvalidEntryFormatException;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author lesya
 */
public class CommandCvsHandler extends CvsHandler {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler");

  protected final CvsOperation myCvsOperation;

  private final List<CvsOperation> myPostActivities = new ArrayList<>();

  private final boolean myCanBeCanceled;
  protected boolean myIsCanceled = false;
  private PerformInBackgroundOption myBackgroundOption;

  public CommandCvsHandler(String title, CvsOperation cvsOperation, boolean canBeCanceled) {
    this(title, cvsOperation, FileSetToBeUpdated.EMPTY, canBeCanceled);
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
    this(title, cvsOperation, FileSetToBeUpdated.EMPTY);
  }

  public CommandCvsHandler(final String title,
                           final CvsOperation operation,
                           final FileSetToBeUpdated files,
                           final PerformInBackgroundOption backgroundOption) {
    this(title, operation, files);
    myBackgroundOption = backgroundOption;
  }

  @Override
  public boolean login(Project project) {
    return loginAll(project);
  }

  @Override
  public boolean canBeCanceled() {
    return myCanBeCanceled;
  }

  @Override
  public PerformInBackgroundOption getBackgroundOption(final Project project) {
    return myBackgroundOption;
  }

  @Override protected boolean runInReadThread() {
    return myCvsOperation.runInReadThread();
  }

  public static CvsHandler createCheckoutFileHandler(FilePath[] files,
                                                     CvsConfiguration configuration,
                                                     @Nullable PerformInBackgroundOption option) {
    return new CommandCvsHandler(CvsBundle.message("operation.name.check.out.files"), new CheckoutFilesOperation(files, configuration),
                                 FileSetToBeUpdated.selectedFiles(files),
                                 (option == null) ? PerformInBackgroundOption.DEAF : option);
  }

  public static CvsHandler createCheckoutHandler(CvsEnvironment environment,
                                                 String[] checkoutPath,
                                                 final File target,
                                                 boolean useAltCheckoutDir,
                                                 boolean makeNewFilesReadOnly, final PerformInBackgroundOption option) {
    final CheckoutProjectOperation checkoutOperation = CheckoutProjectOperation.create(environment, checkoutPath, target,
                                        useAltCheckoutDir, makeNewFilesReadOnly);
    return new CommandCvsHandler(CvsBundle.message("operation.name.check.out.project"), checkoutOperation, FileSetToBeUpdated.allFiles(),
                                 (option == null) ? PerformInBackgroundOption.DEAF : option) {
      @Override
      public void finish() {
        CvsEntriesManager.getInstance().clearAll();
      }
    };
  }

  public static CvsHandler createImportHandler(ImportDetails details) {
    return new CommandCvsHandler(CvsBundle.message("operation.name.import"), new ImportOperation(details), FileSetToBeUpdated.EMPTY);
  }

  public static UpdateHandler createUpdateHandler(final FilePath[] files,
                                                  UpdateSettings updateSettings,
                                                  Project project,
                                                  @NotNull UpdatedFiles updatedFiles) {
    return new UpdateHandler(files, updateSettings, project, updatedFiles);
  }

  public static CvsHandler createTagHandler(FilePath[] selectedFiles, String tagName, boolean switchToThisTag,
                                            boolean overrideExisting, boolean makeNewFilesReadOnly, Project project) {
    final CompositeOperation operation = new CompositeOperation();
    operation.addOperation(new TagOperation(selectedFiles, tagName, false, overrideExisting));
    if (switchToThisTag) {
      operation.addOperation(new UpdateOperation(selectedFiles, tagName, makeNewFilesReadOnly, project));
    }
    return new CommandCvsHandler(CvsBundle.message("operation.name.create.tag"), operation,
                                 FileSetToBeUpdated.selectedFiles(selectedFiles));
  }

  public static CvsHandler createBranchHandler(FilePath[] selectedFiles, String branchName, boolean switchToThisBranch,
                                               boolean overrideExisting, boolean makeNewFilesReadOnly, Project project) {
    final CompositeOperation operation = new CompositeOperation();
    operation.addOperation(new BranchOperation(selectedFiles, branchName, overrideExisting));
    if (switchToThisBranch) {
      operation.addOperation(new UpdateOperation(selectedFiles, branchName, makeNewFilesReadOnly, project));
    }
    return new CommandCvsHandler(CvsBundle.message("operation.name.create.branch"), operation,
                                 FileSetToBeUpdated.selectedFiles(selectedFiles));
  }

  public static CvsHandler createCommitHandler(FilePath[] selectedFiles,
                                               String commitMessage,
                                               String title,
                                               boolean makeNewFilesReadOnly,
                                               Project project,
                                               final boolean tagFilesAfterCommit,
                                               final String tagName,
                                               @NotNull final List<File> dirsToPrune) {
    final CommitFilesOperation operation = new CommitFilesOperation(commitMessage, makeNewFilesReadOnly);
    if (selectedFiles != null) {
      for (FilePath selectedFile : selectedFiles) {
        operation.addFile(selectedFile.getIOFile());
      }
    }
    if (!dirsToPrune.isEmpty()) {
      operation.addFinishAction(() -> {
        final IOFilesBasedDirectoryPruner pruner = new IOFilesBasedDirectoryPruner(null);
        for(File dir: dirsToPrune) {
          pruner.addFile(dir);
        }
        pruner.execute();
      });
    }

    final CommandCvsHandler result = new CommandCvsHandler(title, operation, FileSetToBeUpdated.selectedFiles(selectedFiles));

    if (tagFilesAfterCommit) {
      result.addOperation(new TagOperation(selectedFiles, tagName, false,
                                           CvsConfiguration.getInstance(project).OVERRIDE_EXISTING_TAG_FOR_PROJECT));
    }

    return result;
  }

  public static CvsHandler createAddFilesHandler(final Project project, Collection<AddedFileInfo> addedRoots) {
    final AddFilesOperation operation = new AddFilesOperation();
    final ArrayList<AddedFileInfo> addedFileInfo = new ArrayList<>();
    for (final AddedFileInfo info : addedRoots) {
      info.clearAllCvsAdminDirectoriesInIncludedDirectories();
      addedFileInfo.addAll(info.collectAllIncludedFiles());
    }

    final ArrayList<VirtualFile> addedFiles = new ArrayList<>();

    for (AddedFileInfo info : addedFileInfo) {
      addedFiles.add(info.getFile());
      operation.addFile(info.getFile(), info.getKeywordSubstitution());
    }
    return new CommandCvsHandler(CvsBundle.message("action.name.add"), operation,
                                 FileSetToBeUpdated.selectedFiles(VfsUtilCore.toVirtualFileArray(addedFiles)),
                                 VcsConfiguration.getInstance(project).getAddRemoveOption());
  }

  public static CvsHandler createRemoveFilesHandler(Project project, Collection<File> files) {
    final RemoveFilesOperation operation = new RemoveFilesOperation();
    for (final File file : files) {
      operation.addFile(file.getPath());
    }
    return new CommandCvsHandler(CvsBundle.message("action.name.remove"), operation,
                                 FileSetToBeUpdated.selectedFiles(getAdminDirectoriesFor(files)),
                                 VcsConfiguration.getInstance(project).getAddRemoveOption());
  }

  private static VirtualFile[] getAdminDirectoriesFor(Collection<File> files) {
    final Collection<VirtualFile> result = new HashSet<>();
    for (File file : files) {
      final File parentFile = file.getParentFile();
      final VirtualFile cvsAdminDirectory = CvsVfsUtil.findFileByIoFile(new File(parentFile, CvsUtil.CVS));
      if (cvsAdminDirectory != null) result.add(cvsAdminDirectory);
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  public static CvsHandler createRestoreFileHandler(final VirtualFile parent,
                                                    String name,
                                                    boolean makeNewFilesReadOnly) {
    final File ioFile = new File(VfsUtilCore.virtualToIoFile(parent), name);

    final Entry entry = CvsEntriesManager.getInstance().getEntryFor(parent, name);

    final String revision = getRevision(entry);

    final CheckoutFileOperation operation =
      new CheckoutFileOperation(parent, new SimpleRevision(revision), name, makeNewFilesReadOnly);
    final CommandCvsHandler cvsHandler =
      new CommandCvsHandler(CvsBundle.message("operation.name.restore"), operation, FileSetToBeUpdated.EMPTY);

    operation.addFinishAction(() -> {
      final List<VcsException> errors = cvsHandler.getErrors();
      if (errors != null && !errors.isEmpty()) return;

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

    });

    return cvsHandler;
  }

  public static CvsHandler createEditHandler(VirtualFile[] selectedFiles, boolean isReservedEdit) {
    final EditOperation operation = new EditOperation(isReservedEdit);
    operation.addFiles(selectedFiles);
    return new CommandCvsHandler(CvsBundle.message("action.name.edit"), operation, FileSetToBeUpdated.selectedFiles(selectedFiles));
  }

  public static CvsHandler createUneditHandler(VirtualFile[] selectedFiles, boolean makeNewFilesReadOnly) {
    final UneditOperation operation = new UneditOperation(makeNewFilesReadOnly);
    operation.addFiles(selectedFiles);
    return new CommandCvsHandler(CvsBundle.message("operation.name.unedit"), operation, FileSetToBeUpdated.selectedFiles(selectedFiles));
  }

  public static CvsHandler createRemoveTagAction(FilePath[] selectedFiles, String tagName) {
    return new CommandCvsHandler(CvsBundle.message("action.name.delete.tag"), new TagOperation(selectedFiles, tagName, true, false),
                                 FileSetToBeUpdated.EMPTY);
  }

  @Nullable
  private static String getRevision(final Entry entry) {
    if (entry == null) {
      return null;
    }
    final String result = entry.getRevision();
    if (result == null) return null;
    if (StringUtil.startsWithChar(result, '-')) return result.substring(1);
    return result;
  }

  @Override
  public boolean isCanceled() {
    return myIsCanceled;
  }

  private boolean loginAll(Project project) {
    final Set<CvsEnvironment> allRoots = new HashSet<>();
    try {
      myCvsOperation.appendSelfCvsRootProvider(allRoots);
      for (CvsOperation postActivity : myPostActivities) {
        postActivity.appendSelfCvsRootProvider(allRoots);
      }
    }
    catch (CannotFindCvsRootException e) {
      myErrors.add(new VcsException(e));
      return false;
    }

    final LoginPerformer performer = new LoginPerformer(project, allRoots, e -> myErrorMessageProcessor.addError(e));
    performer.setForceCheck(true);
    return performer.loginAll();
  }

  @Override
  public void internalRun(Project project, final ModalityContext executor, final boolean runInReadAction) {
    if (! login(project)) return;

    final CvsExecutionEnvironment executionEnvironment = new CvsExecutionEnvironment(myCompositeListener,
                                                                                     getProgressListener(),
                                                                                     myErrorMessageProcessor,
                                                                                     getPostActivityHandler(),
                                                                                     project);
    if (! runOperation(executionEnvironment, runInReadAction, myCvsOperation)) return;
    onOperationFinished(executor);

    while (!myPostActivities.isEmpty()) {
      final CvsOperation cvsOperation = myPostActivities.get(0);
      if (! runOperation(executionEnvironment, runInReadAction, cvsOperation)) return;
      myPostActivities.remove(cvsOperation);
    }
  }

  private boolean runOperation(final CvsExecutionEnvironment executionEnvironment,
                               final boolean runInReadAction,
                               final CvsOperation cvsOperation) {
    try {
      cvsOperation.execute(executionEnvironment, runInReadAction);
      return true;
    }
    catch (VcsException e) {
      myErrors.add(e);
    }
    catch (InvalidModuleDescriptionException ex) {
      myErrors.add(new CvsException(ex, ex.getCvsRoot()));
    }
    catch (InvalidEntryFormatException e) {
      myErrors.add(new VcsException(CvsBundle.message("exception.text.entries.file.is.corrupted", e.getEntriesFile())));
    }
    catch (CvsProcessException ex) {
      myErrors.add(new CvsException(ex, cvsOperation.getLastProcessedCvsRoot()));
    }
    catch (CommandAbortedException ex) {
      LOG.error(ex);
      myErrors.add(new CvsException(ex, cvsOperation.getLastProcessedCvsRoot()));
    }
    catch(ProcessCanceledException ex) {
      myIsCanceled = true;
    }
    catch (Exception ex) {
      LOG.error(ex);
      myErrors.add(new CvsException(ex, myCvsOperation.getLastProcessedCvsRoot()));
    }
    return false;
  }

  protected void onOperationFinished(ModalityContext modalityContext) {}

  protected void addFileToCheckout(VirtualFile file) {
    addOperation(new CheckoutFileOperation(file.getParent(), RevisionOrDateImpl.createOn(file), file.getName(), false));
  }

  protected void addOperation(CvsOperation operation) {
    myPostActivities.add(operation);
  }

  protected PostCvsActivity getPostActivityHandler() {
    return PostCvsActivity.DEAF;
  }

  @Override
  protected int getFilesToProcessCount() {
    return myCvsOperation.getFilesToProcessCount();
  }

  public static CvsHandler createGetFileFromRepositoryHandler(CvsLightweightFile[] cvsLightweightFiles, boolean makeNewFilesReadOnly) {
    final CompositeOperation compositeOperation = new CompositeOperation();
    final CvsEntriesManager entriesManager = CvsEntriesManager.getInstance();
    for (CvsLightweightFile cvsLightweightFile : cvsLightweightFiles) {
      final File root = cvsLightweightFile.getRoot();
      File workingDirectory = root;
      if (workingDirectory == null) continue;
      if (cvsLightweightFile.getLocalFile().getParentFile().equals(workingDirectory)) {
        workingDirectory = workingDirectory.getParentFile();
      }
      final String alternativeCheckoutPath = getAlternativeCheckoutPath(cvsLightweightFile, workingDirectory);
      final CheckoutProjectOperation checkoutFileOperation = new CheckoutProjectOperation(new String[]{cvsLightweightFile.getModuleName()},
                                                                                          entriesManager.getCvsConnectionSettingsFor(root),
                                                                                          makeNewFilesReadOnly,
                                                                                          workingDirectory,
                                                                                          alternativeCheckoutPath,
                                                                                          true,
                                                                                          null);
      compositeOperation.addOperation(checkoutFileOperation);
    }
    return new CommandCvsHandler(CvsBundle.message("action.name.get.file.from.repository"),
                                 compositeOperation, FileSetToBeUpdated.allFiles(), true);
  }

  private static String getAlternativeCheckoutPath(CvsLightweightFile cvsLightweightFile, File workingDirectory) {
    final File parent = cvsLightweightFile.getLocalFile().getParentFile();
    return parent.getAbsolutePath().substring(workingDirectory.getAbsolutePath().length());
  }
}
