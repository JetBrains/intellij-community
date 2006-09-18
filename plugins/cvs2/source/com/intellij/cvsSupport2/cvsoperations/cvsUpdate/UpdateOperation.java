package com.intellij.cvsSupport2.cvsoperations.cvsUpdate;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.cvsSupport2.actions.update.UpdateByBranchUpdateSettings;
import com.intellij.cvsSupport2.actions.update.UpdateSettings;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.common.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.ModuleLevelVcsManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.update.UpdateCommand;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.io.File;

/**
 * author: lesya
 */

public class UpdateOperation extends CvsOperationOnFiles {

  private final UpdateSettings myUpdateSettings;
  private final Project myProject;

  public UpdateOperation(FilePath[] files, UpdateSettings updateSettings, Project project) {
    myUpdateSettings = updateSettings;
    myProject = project;
    addAllFiles(files);
  }

  public void addAllFiles(FilePath[] files) {
    for (FilePath file : files) {
      addFile(file.getIOFile());
    }
  }

  public void addAllFiles(VirtualFile[] files) {
    for (VirtualFile file : files) {
      addFile(new File(file.getPath()));
    }
  }

  protected String getOperationName() {
    return "update";
  }

  public UpdateOperation(UpdateSettings updateSettings, Project project) {
    this(new FilePath[0], updateSettings, project);
  }

  public UpdateOperation(FilePath[] files, String branchName,
                         boolean makeNewFilesReadOnly, Project project) {
    this(files, new UpdateByBranchUpdateSettings(branchName, makeNewFilesReadOnly), project);
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    UpdateCommand updateCommand = new UpdateCommand();
    addFilesToCommand(root, updateCommand);
    //updateCommand.setPruneDirectories(myUpdateSettings.getPruneEmptyDirectories());
    updateCommand.setCleanCopy(myUpdateSettings.getCleanCopy());
    updateCommand.setResetStickyOnes(myUpdateSettings.getResetAllSticky());
    updateCommand.setBuildDirectories(myUpdateSettings.getCreateDirectories());
    updateCommand.setKeywordSubst(myUpdateSettings.getKeywordSubstitution());

    myUpdateSettings.getRevisionOrDate().setForCommand(updateCommand);
    if (myUpdateSettings.getBranch1ToMergeWith() != null) {
      updateCommand.setMergeRevision1(myUpdateSettings.getBranch1ToMergeWith());
    }
    if (myUpdateSettings.getBranch2ToMergeWith() != null) {
      updateCommand.setMergeRevision2(myUpdateSettings.getBranch2ToMergeWith());
    }

    return updateCommand;
  }


  public void modifyOptions(GlobalOptions options) {
    super.modifyOptions(options);
    options.setDoNoChanges(myUpdateSettings.getDontMakeAnyChanges());
    options.setCheckedOutFilesReadOnly(myUpdateSettings.getMakeNewFilesReadOnly());
  }

  public int getFilesToProcessCount() {
    return CvsHandler.UNKNOWN_COUNT;
  }

  public boolean fileIsUnderProject(VirtualFile file) {
    if (!super.fileIsUnderProject(file)) return false;
    Module module = VfsUtil.getModuleForFile(myProject, file);
    if (module == null) return false;
    return ModuleLevelVcsManager.getInstance(module).getActiveVcs() == CvsVcs2.getInstance(myProject);
  }

  protected IIgnoreFileFilter getIgnoreFileFilter() {
    final IIgnoreFileFilter ignoreFileFilterFromSuper = super.getIgnoreFileFilter();
    return new IIgnoreFileFilter() {
      public boolean shouldBeIgnored(AbstractFileObject abstractFileObject, ICvsFileSystem cvsFileSystem) {
        if (ignoreFileFilterFromSuper.shouldBeIgnored(abstractFileObject, cvsFileSystem)) {
          return true;
        }
        VirtualFile fileByIoFile = CvsVfsUtil.findFileByIoFile(cvsFileSystem.getLocalFileSystem().getFile(abstractFileObject));
        if (fileByIoFile == null) return false;
        Module module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(fileByIoFile);
        if (module == null) return false;
        return ModuleLevelVcsManager.getInstance(module).getActiveVcs() != CvsVcs2.getInstance(myProject);
      }
    };
  }

  protected ReceivedFileProcessor createReceivedFileProcessor(UpdatedFilesManager mergedFilesCollector,
                                                              PostCvsActivity postCvsActivity, ModalityContext modalityContext) {
    return new UpdateReceivedFileProcessor(mergedFilesCollector,
                                           postCvsActivity);
  }

  @Override public boolean runInReadThread() {
    return false;
  }
}
