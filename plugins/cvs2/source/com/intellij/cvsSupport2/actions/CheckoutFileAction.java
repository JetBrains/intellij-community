package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsCheckOut.ui.CheckoutFileDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.ui.OptionsDialog;
import com.intellij.openapi.vcs.ui.ReplaceFileConfirmationDialog;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * author: lesya
 */
public class CheckoutFileAction extends ActionOnSelectedElement {

  private Collection myModifiedFiles;

  public CheckoutFileAction() {
    super(true);
    CvsActionVisibility visibility = getVisibility();
    visibility.canBePerformedOnSeveralFiles();
    visibility.canBePerformedOnLocallyDeletedFile();
    visibility.addCondition(FILES_EXIST_IN_CVS);
  }

  protected String getTitle(VcsContext context) {
    return "Check Out";
  }

  public void update(AnActionEvent e) {
    super.update(e);
    if (!e.getPresentation().isVisible()) {
      return;
    }
    CvsConfiguration config = getConfig(e);
    if (config == null) return;
    adjustName(config.SHOW_CHECKOUT_OPTIONS, e);
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    if (myModifiedFiles != null) {
      if (!myModifiedFiles.isEmpty()) {
        if (!new ReplaceFileConfirmationDialog(context.getProject(), "Checkout").requestConfirmation(myModifiedFiles)) {
          return CvsHandler.NULL;
        }

      }
    }

    myModifiedFiles = null;

    Project project = context.getProject();
    FilePath[] filesArray = context.getSelectedFilePaths();
    List<FilePath> files = Arrays.asList(filesArray);
    if (CvsConfiguration.getInstance(project).SHOW_CHECKOUT_OPTIONS
        || OptionsDialog.shiftIsPressed(context.getModifiers())){
      CheckoutFileDialog checkoutFileDialog = new CheckoutFileDialog(project,
                                                                     files);
      checkoutFileDialog.show();
      if (!checkoutFileDialog.isOK()) return CvsHandler.NULL;
    }

    return CommandCvsHandler.createCheckoutFileHandler(filesArray, CvsConfiguration.getInstance(project));
  }

  protected void beforeActionPerformed(VcsContext context) {
    super.beforeActionPerformed(context);
    myModifiedFiles =
      new ReplaceFileConfirmationDialog(context.getProject(), "Checkout").collectModifiedFiles(context.getSelectedFiles());
  }
}
