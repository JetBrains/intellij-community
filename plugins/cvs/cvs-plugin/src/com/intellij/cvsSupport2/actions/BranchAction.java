package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.CreateTagDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.CvsBundle;

import java.util.Arrays;

/**
 * author: lesya
 */
public class BranchAction extends ActionOnSelectedElement{

  public BranchAction() {
    super(true);
    CvsActionVisibility visibility = getVisibility();
    visibility.canBePerformedOnSeveralFiles();
    visibility.canBePerformedOnLocallyDeletedFile();
    visibility.addCondition(FILES_EXIST_IN_CVS);
  }

  protected String getTitle(VcsContext context) {
    return CvsBundle.message("operation.name.create.branch");
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    FilePath[] selectedFiles = context.getSelectedFilePaths();
    Project project = context.getProject();
    CreateTagDialog createBranchDialog = new CreateTagDialog(Arrays.asList(selectedFiles),
                                                             project, false);
    createBranchDialog.show();
    if (!createBranchDialog.isOK()) return CvsHandler.NULL;

    return CommandCvsHandler.createBranchOrTagHandler(selectedFiles,
        createBranchDialog.getTagName(),
        createBranchDialog.switchToThisBranch(),
        createBranchDialog.getOverrideExisting(),
        false, CvsConfiguration.getInstance(context.getProject()).MAKE_NEW_FILES_READONLY, project);
  }
}
