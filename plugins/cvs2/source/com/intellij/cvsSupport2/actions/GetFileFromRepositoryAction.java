package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsLightweightFile;
import com.intellij.cvsSupport2.actions.update.UpdateSettingsOnCvsConfiguration;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.CvsBundle;

public class GetFileFromRepositoryAction extends ActionOnSelectedElement{
  public GetFileFromRepositoryAction() {
    super(true);
    CvsActionVisibility visibility = getVisibility();
    visibility.canBePerformedOnSeveralFiles();
    visibility.canBePerformedOnCvsLightweightFile();
    visibility.shouldNotBePerformedOnDirectory();
  }

  protected String getTitle(VcsContext context) {
    return CvsBundle.message("action.name.get.file.from.repository");
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    CvsLightweightFile[] cvsLightweightFiles = context.getSelectedLightweightFiles();
    Project project = context.getProject();
    if (cvsLightweightFiles != null) {
      boolean makeNewFilesReadOnly = project != null && CvsConfiguration.getInstance(project).MAKE_NEW_FILES_READONLY;
      return CommandCvsHandler.createGetFileFromRepositoryHandler(cvsLightweightFiles, makeNewFilesReadOnly);
    }
    final FilePath[] filePaths = context.getSelectedFilePaths();
    if (filePaths != null) {
      CvsConfiguration cvsConfiguration = CvsConfiguration.getInstance(project);
      final UpdateSettingsOnCvsConfiguration updateSettings = new UpdateSettingsOnCvsConfiguration(cvsConfiguration,
                                                                                                   cvsConfiguration.CLEAN_COPY,
                                                                                                   cvsConfiguration.RESET_STICKY);
      return CommandCvsHandler.createUpdateHandler(filePaths, updateSettings, project, UpdatedFiles.create());
    }
    return CvsHandler.NULL;
  }
}