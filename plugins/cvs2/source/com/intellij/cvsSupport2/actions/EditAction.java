package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui.EditOptionsDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.OptionsDialog;
import com.intellij.openapi.vcs.actions.VcsContext;

/**
 * author: lesya
 */
public class EditAction extends AsbtractActionFromEditGroup {
  public EditAction() {
  }

  public void update(AnActionEvent e) {
    super.update(e);
    if (!e.getPresentation().isVisible()) {
      return;
    }
    CvsConfiguration config = getConfig(e);
    if (config == null) return;

    adjustName(config.SHOW_EDIT_DIALOG, e);
  }


  protected String getTitle(VcsContext context) {
    return "Edit";
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    Project project = context.getProject();
    CvsConfiguration configuration = CvsConfiguration.getInstance(project);
    if (configuration.SHOW_EDIT_DIALOG|| OptionsDialog.shiftIsPressed(context.getModifiers())) {
      EditOptionsDialog editOptionsDialog = new EditOptionsDialog(project);
      editOptionsDialog.show();
      if (!editOptionsDialog.isOK()) return CvsHandler.NULL;
    }

    return CommandCvsHandler.createEditHandler(context.getSelectedFiles(),
                                               configuration.RESERVED_EDIT);
  }
}
