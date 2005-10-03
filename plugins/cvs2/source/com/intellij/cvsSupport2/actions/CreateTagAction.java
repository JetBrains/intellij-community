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

import java.util.Arrays;

/**
 * author: lesya
 */
public class CreateTagAction extends ActionOnSelectedElement{

  public CreateTagAction() {
    super(true);
    CvsActionVisibility visibility = getVisibility();
    visibility.canBePerformedOnSeveralFiles();
    visibility.canBePerformedOnLocallyDeletedFile();
    visibility.addCondition(FILES_EXIST_IN_CVS);
  }

  protected String getTitle(VcsContext context) {
    return com.intellij.CvsBundle.message("operation.name.create.tag");
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    FilePath[] selectedFiles = context.getSelectedFilePaths();
    Project project = context.getProject();
    CreateTagDialog dialog = new CreateTagDialog(Arrays.asList(selectedFiles),
                                                 project, true);
    dialog.show();
    if (!dialog.isOK())
      return CvsHandler.NULL;
    return CommandCvsHandler.createBranchOrTagHandler(selectedFiles, dialog.getTagName(),
            dialog.switchToThisBranch(), dialog.getOverrideExisting(),
            true, CvsConfiguration.getInstance(project).MAKE_NEW_FILES_READONLY, project);
  }

}
