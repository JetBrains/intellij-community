package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.cvsSupport2.actions.update.UpdateSettings;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsoperations.common.FindAllRoots;
import com.intellij.cvsSupport2.cvsoperations.common.PostCvsActivity;
import com.intellij.cvsSupport2.cvsoperations.cvsUpdate.MergedWithConflictProjectOrModuleFile;
import com.intellij.cvsSupport2.cvsoperations.cvsUpdate.UpdateOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsUpdate.ui.CorruptedProjectFilesDialog;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Options;
import com.intellij.util.containers.HashSet;
import com.intellij.CvsBundle;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * author: lesya
 */
public class UpdateHandler extends CommandCvsHandler implements PostCvsActivity {
  private final FilePath[] myFiles;
  private final Collection<VirtualFile> myRoots = new ArrayList<VirtualFile>();
  private final Collection<File> myNotProcessedRepositories = new HashSet<File>();
  private double myDirectoriesToBeProcessedCount;

  private final static CvsMessagePattern UPDATE_PATTERN = new CvsMessagePattern(new String[]{"cvs server: Updating ", "*"}, 2);
  private final Project myProject;
  private final Collection<MergedWithConflictProjectOrModuleFile> myCorruptedFiles = new ArrayList<MergedWithConflictProjectOrModuleFile>();
  private final UpdatedFiles myUpdatedFiles;
  private final UpdateSettings myUpdateSettings;

  public UpdateHandler(FilePath[] files, UpdateSettings updateSettings, Project project, UpdatedFiles updatedFiles) {
    super(CvsBundle.message("operation.name.update"), new UpdateOperation(new FilePath[0], updateSettings, project),
          FileSetToBeUpdated.selectedFiles(files));
    myFiles = files;
    myProject = project;
    myUpdatedFiles = updatedFiles;
    myUpdateSettings = updateSettings;
  }

  public void beforeLogin() {
    try {
      super.beforeLogin();
      FindAllRoots findAllRoots = new FindAllRoots(myProject);
      myRoots.addAll(findAllRoots.executeOn(myFiles));
      myNotProcessedRepositories.addAll(findAllRoots.getDirectoriesToBeUpdated());
      myDirectoriesToBeProcessedCount = myNotProcessedRepositories.size();
      for(VirtualFile file: myRoots) {
        if (getValidCvsRoot(file) != null) {
          ((UpdateOperation)myCvsOperation).addFile(file);
        }
      }
    }
    catch (ProcessCanceledException ex) {
      myIsCanceled = true;
    }

  }

  @Nullable
  private static CvsRootProvider getValidCvsRoot(final VirtualFile file) {
    try {
      return CvsRootProvider.createOn(new File(file.getPath()));
    }
    catch (CannotFindCvsRootException e) {
      return null;
    }
  }

  public void addFileMessage(String message, ICvsFileSystem cvsFileSystem) {
    super.addFileMessage(message, cvsFileSystem);
    ProgressIndicator progress = getProgress();
    if (progress == null) return;
    if (UPDATE_PATTERN.matches(message)) {
      String relativeFileName = UPDATE_PATTERN.getRelativeFileName(message);
      myNotProcessedRepositories.remove(cvsFileSystem.getLocalFileSystem().getFile(relativeFileName));
      int notProcessedSize = myNotProcessedRepositories.size();
      progress.setFraction(0.5 + (myDirectoriesToBeProcessedCount - notProcessedSize) / (2 * myDirectoriesToBeProcessedCount));
    }
  }

  public void registerCorruptedProjectOrModuleFile(MergedWithConflictProjectOrModuleFile mergedWithConflictProjectOrModuleFile) {
    myCorruptedFiles.add(mergedWithConflictProjectOrModuleFile);
  }

  protected void onOperationFinished(ModalityContext modalityContext) {

    if (myUpdateSettings.getPruneEmptyDirectories()) {
      final IOFilesBasedDirectoryPruner pruner = new IOFilesBasedDirectoryPruner(ProgressManager.getInstance().getProgressIndicator());
      for (final VirtualFile myRoot : myRoots) {
        pruner.addFile(new File(myRoot.getPath()));
      }

      pruner.execute();
    }

    if (!myCorruptedFiles.isEmpty()) {
      int showOptions = CvsConfiguration.getInstance(myProject).SHOW_CORRUPTED_PROJECT_FILES;

      if (showOptions == Options.PERFORM_ACTION_AUTOMATICALLY) {
        for (final MergedWithConflictProjectOrModuleFile myCorruptedFile : myCorruptedFiles) {
          myCorruptedFile.setShouldBeCheckedOut();
        }
      }
      else if (showOptions == Options.SHOW_DIALOG){
        modalityContext.runInDispatchThread(new Runnable() {
          public void run() {
            new CorruptedProjectFilesDialog(myProject, myCorruptedFiles).show();
          }
        });

      }

      for (final MergedWithConflictProjectOrModuleFile myCorruptedFile : myCorruptedFiles) {
        if (myCorruptedFile.shouldBeCheckedOut()) {
          addFileToCheckout(myCorruptedFile.getOriginal());
        }
        else {
          myUpdatedFiles.getGroupById(FileGroup.MODIFIED_ID).add(myCorruptedFile.getOriginal().getPath());
        }
      }

    }

  }

  protected PostCvsActivity getPostActivityHandler() {
    return this;
  }
}
