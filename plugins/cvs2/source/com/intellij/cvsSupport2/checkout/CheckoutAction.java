package com.intellij.cvsSupport2.checkout;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.AbstractAction;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.cvsBrowser.CvsElement;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.cvsSupport2.ui.experts.checkout.CheckoutWizard;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.actions.VcsContext;

import java.io.File;

public class CheckoutAction extends AbstractAction {
  private CvsElement[] mySelectedElements;
  private File myCheckoutDirectory;
  private boolean myUseAlternativeCheckoutPath;

  public CheckoutAction() {
    super(true);
  }

  public CheckoutAction(final CvsElement[] selectedElements, final File checkoutDirectory, final boolean useAlternativeCheckoutPath) {
    super(true);

    mySelectedElements = selectedElements;
    myCheckoutDirectory = checkoutDirectory;
    myUseAlternativeCheckoutPath = useAlternativeCheckoutPath;
  }

  protected String getTitle(VcsContext context) {
    return com.intellij.CvsBundle.message("operation.name.check.out.project");
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

    CvsVcs2.getInstance(context.getProject()).getCheckoutProvider().refreshAfterCheckout(
        ProjectLevelVcsManager.getInstance(context.getProject()).getCompositeCheckoutListener(), mySelectedElements,
        myCheckoutDirectory, myUseAlternativeCheckoutPath);
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setVisible(true);
    presentation.setEnabled(true);
  }
}
