package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.cvsBrowser.CvsElement;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.cvsSupport2.ui.experts.checkout.CheckoutWizard;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vcs.actions.VcsContext;

import java.io.File;

public class CheckoutAction extends AbstractAction {
  private File myCheckoutDirectory;
  private CvsElement[] mySelectedElements;
  private boolean myUseAlternativeCheckoutPath = false;

  public CheckoutAction() {
    super(true);
  }

  protected String getTitle(VcsContext context) {
    return "Check Out Project";
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    CheckoutWizard checkoutWizard = new CheckoutWizard(context.getProject());
    checkoutWizard.show();
    if (!checkoutWizard.isOK()) return CvsHandler.NULL;
    myUseAlternativeCheckoutPath = checkoutWizard.useAlternativeCheckoutLocation();
    myCheckoutDirectory = checkoutWizard.getCheckoutDirectory();

    mySelectedElements = checkoutWizard.getSelectedElements();
    return CommandCvsHandler.createCheckoutHandler(
      checkoutWizard.getSelectedConfiguration(),
      collectCheckoutPaths(),
      myCheckoutDirectory,
      myUseAlternativeCheckoutPath,
      CvsApplicationLevelConfiguration.getInstance().MAKE_CHECKED_OUT_FILES_READONLY
    );
  }

  private String[] collectCheckoutPaths() {
    String[] checkoutPaths = new String[mySelectedElements.length];
    for (int i = 0; i < mySelectedElements.length; i++) {
      CvsElement selectedElement = mySelectedElements[i];
      checkoutPaths[i] = selectedElement.getCheckoutPath();
    }
    return checkoutPaths;
  }

  protected void onActionPerformed(final CvsContext context,
                                   CvsTabbedWindow tabbedWindow,
                                   boolean successfully,
                                   CvsHandler handler) {
    super.onActionPerformed(context, tabbedWindow, successfully, handler);
    if (successfully) {

      if (mySelectedElements == null) return;
      //for (int i = 0; i < mySelectedElements.length; i++) {
        //CvsElement selectedElement = mySelectedElements[i];
        //selectedElement.collectProjectElements(new GetContentCallback() {
        //  public void fillDirectoryContent(List directoryContent) {
        //    final Collection projectFiles = collectCheckoutFiles(directoryContent);
        //    if (projectFiles.isEmpty()) return;
        //    LaterInvocatorEx.invokeLater(new Runnable() {
        //      public void run() {
        //        SelectFileToOpenAsProjectDialod dialog = new SelectFileToOpenAsProjectDialod(projectFiles);
        //        dialog.show();
        //        if (dialog.isOK()) {
        //          Project newProject = ProjectUtil.openProject(dialog.getSelectedFile().getAbsolutePath(),
        //                                                       context.getProject());
        //          if (newProject != null) {
        //            ModuleLevelVcsManager.getInstance(newProject).setActiveVcs(CvsVcs2.getInstance(newProject));
        //            CvsConfiguration.getInstance(newProject).MAKE_NEW_FILES_READONLY = CvsApplicationLevelConfiguration.getInstance()
        //              .MAKE_CHECKED_OUT_FILES_READONLY;
        //          }
        //
        //        }
        //      }
        //    }, ModalityState.NON_MMODAL);
        //  }
        //
        //  public void loginAborted() {
        //  }
        //
        //  public void finished() {
        //  }
        //});

//      }
    }
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setVisible(true);
    presentation.setEnabled(true);
  }
}
