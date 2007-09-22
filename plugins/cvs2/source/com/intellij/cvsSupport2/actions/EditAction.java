package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui.EditOptionsDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.util.ui.OptionsDialog;

/**
 * author: lesya
 */
public class EditAction extends AbstractActionFromEditGroup {
  public EditAction() {
  }

  public void update(AnActionEvent e) {
    super.update(e);
    if (!e.getPresentation().isVisible()) {
      return;
    }
    Project project = CvsContextWrapper.createInstance(e).getProject();

    if (project == null) return;

    adjustName(CvsVcs2.getInstance(project).getEditOptions().getValue(), e);
  }


  protected String getTitle(VcsContext context) {
    return com.intellij.CvsBundle.message("action.name.edit");
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    Project project = context.getProject();
    if (CvsVcs2.getInstance(project).getEditOptions().getValue()
        || OptionsDialog.shiftIsPressed(context.getModifiers())) {
      EditOptionsDialog editOptionsDialog = new EditOptionsDialog(project);
      editOptionsDialog.show();
      if (!editOptionsDialog.isOK()) return CvsHandler.NULL;
    }

    return CommandCvsHandler.createEditHandler(context.getSelectedFiles(),
                                               CvsConfiguration.getInstance(project).RESERVED_EDIT);
  }
}
