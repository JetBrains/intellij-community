package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsLightweightFile;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.actions.VcsContext;

public class GetFileFromRepositoryAction extends ActionOnSelectedElement{
  public GetFileFromRepositoryAction() {
    super(true);
    CvsActionVisibility visibility = getVisibility();
    visibility.canBePerformedOnSeveralFiles();
    visibility.canBePerformedOnCvsLightweightFile();
    visibility.shouldNotBePerformedOnDirectory();
  }

  protected String getTitle(VcsContext context) {
    return com.intellij.CvsBundle.message("action.name.get.file.from.repository");
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    CvsLightweightFile[] cvsLightweightFiles = context.getSelectedLightweightFiles();
    if (cvsLightweightFiles == null){
      return CvsHandler.NULL;
    }
    Project project = context.getProject();
    boolean makeNewFilesReadOnly = project == null ? false : CvsConfiguration.getInstance(project).MAKE_NEW_FILES_READONLY;
    return CommandCvsHandler.createGetFileFromRepositoryHandler(cvsLightweightFiles, makeNewFilesReadOnly);
  }
}