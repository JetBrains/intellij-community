package com.intellij.cvsSupport2.checkout;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.cvsBrowser.CvsElement;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.ui.experts.checkout.CheckoutWizard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.File;

public class CvsCheckoutProvider implements CheckoutProvider {
  public void doCheckout(final CheckoutProvider.Listener listener) {

    final File checkoutDirectory;
    final CvsElement[] selectedElements;

    CheckoutWizard checkoutWizard = new CheckoutWizard(null);
    checkoutWizard.show();
    if (!checkoutWizard.isOK()) return;
    final boolean useAlternateCheckoutPath = checkoutWizard.useAlternativeCheckoutLocation();
    checkoutDirectory = checkoutWizard.getCheckoutDirectory();

    selectedElements = checkoutWizard.getSelectedElements();
    final CvsHandler checkoutHandler = CommandCvsHandler.createCheckoutHandler(
      checkoutWizard.getSelectedConfiguration(),
      collectCheckoutPaths(selectedElements),
      checkoutDirectory,
      useAlternateCheckoutPath,
      CvsApplicationLevelConfiguration.getInstance().MAKE_CHECKED_OUT_FILES_READONLY
    );

    final CvsOperationExecutor executor = new CvsOperationExecutor(null);
    executor.performActionSync(checkoutHandler, CvsOperationExecutorCallback.EMPTY);

    if (!executor.hasNoErrors()) {
      Messages.showErrorDialog(CvsBundle.message("message.error.checkout", executor.getResult().composeError().getLocalizedMessage()),
                               CvsBundle.message("operation.name.check.out.project"));
    }

    VirtualFileManager.getInstance().refresh(true, new Runnable() {
      public void run() {
        // shouldn't hold write action when calling this (IDEADEV-20086)
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            for (CvsElement element : selectedElements) {
              File path = useAlternateCheckoutPath ? checkoutDirectory : new File(checkoutDirectory, element.getCheckoutPath());
              listener.directoryCheckedOut(path);
            }
            listener.checkoutCompleted();
          }
        });
      }
    });
  }

  private static String[] collectCheckoutPaths(final CvsElement[] mySelectedElements) {
    String[] checkoutPaths = new String[mySelectedElements.length];
    for (int i = 0; i < mySelectedElements.length; i++) {
      CvsElement selectedElement = mySelectedElements[i];
      checkoutPaths[i] = selectedElement.getCheckoutPath();
    }
    return checkoutPaths;
  }

  public String getVcsName() {
    return "_CVS";
  }
}
