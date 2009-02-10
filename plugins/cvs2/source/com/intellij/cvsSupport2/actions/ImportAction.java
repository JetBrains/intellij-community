package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.cvsSupport2.config.ImportConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsImport.ImportDetails;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.cvsSupport2.ui.experts.importToCvs.ImportWizard;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.CvsBundle;

public class ImportAction extends ActionOnSelectedElement {
  private ImportDetails myImportDetails;

  public ImportAction() {
    super(false);
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(true);
  }

  protected String getTitle(VcsContext context) {
    return CvsBundle.message("operation.name.import");
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    VirtualFile selectedFile = context.getSelectedFile();
    ImportWizard importWizard = new ImportWizard(context.getProject(), selectedFile);
    importWizard.show();
    if (!importWizard.isOK()) return CvsHandler.NULL;

    myImportDetails = importWizard.createImportDetails();
    return CommandCvsHandler.createImportHandler(myImportDetails);
  }

  protected void onActionPerformed(CvsContext context,
                                   CvsTabbedWindow tabbedWindow,
                                   boolean successfully,
                                   CvsHandler handler) {
    super.onActionPerformed(context, tabbedWindow, successfully, handler);
    ImportConfiguration importConfiguration = ImportConfiguration.getInstance();
    if (successfully && importConfiguration.CHECKOUT_AFTER_IMPORT) {
      createCheckoutAction(importConfiguration.MAKE_NEW_FILES_READ_ONLY).actionPerformed(context);
    }
  }

  private AbstractAction createCheckoutAction(final boolean makeNewFilesReadOnly) {
    return new AbstractAction(false) {
      protected String getTitle(VcsContext context) {
        return CvsBundle.message("operation.name.checkout.project");
      }

      protected CvsHandler getCvsHandler(CvsContext context) {
        final Project project = context.getProject();
        return CommandCvsHandler.createCheckoutHandler(myImportDetails.getCvsRoot(),
                                                       new String[]{myImportDetails.getModuleName()},
                                                       myImportDetails.getWorkingDirectory(),
                                                       true, makeNewFilesReadOnly,
                                                       project == null ? null : VcsConfiguration.getInstance(project).getCheckoutOption());
      }

      protected void onActionPerformed(CvsContext context,
                                       CvsTabbedWindow tabbedWindow,
                                       boolean successfully,
                                       CvsHandler handler) {
        super.onActionPerformed(context, tabbedWindow, successfully, handler);
        Project project = context.getProject();
        if (successfully) {
          if (project != null) {
            //TODO inherit cvs for all modules
            //ModuleLevelVcsManager.getInstance(project).setActiveVcs(CvsVcs2.getInstance(project));
          }

        }
      }
    };
  }
}
