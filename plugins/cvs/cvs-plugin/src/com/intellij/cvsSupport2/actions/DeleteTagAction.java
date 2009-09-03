package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.DeleteTagDialog;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContext;

import java.util.Arrays;
import java.util.Collection;

/**
 * author: lesya
 */
public class DeleteTagAction extends ActionOnSelectedElement{

  public DeleteTagAction() {
    super(false);
    CvsActionVisibility visibility = getVisibility();
    visibility.canBePerformedOnSeveralFiles();
    visibility.addCondition(FILES_EXIST_IN_CVS);
  }

  protected String getTitle(VcsContext context) {
    return com.intellij.CvsBundle.message("action.name.delete.tag");
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    DeleteTagDialog deleteTagDialog = new DeleteTagDialog(collectFiles(context),
                                                          context.getProject());
    deleteTagDialog.show();
    if (!deleteTagDialog.isOK())
      return CvsHandler.NULL;
    return CommandCvsHandler.createRemoveTagAction(context.getSelectedFiles(),
        deleteTagDialog.getTagName());
  }

  private Collection<FilePath> collectFiles(VcsContext context) {
    return Arrays.asList(context.getSelectedFilePaths());
  }

}
