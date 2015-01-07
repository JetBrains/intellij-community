/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.checkout;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.cvsBrowser.CvsElement;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.ui.experts.checkout.CheckoutWizard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class CvsCheckoutProvider implements CheckoutProvider {
  public void doCheckout(@NotNull final Project project, final CheckoutProvider.Listener listener) {

    final CheckoutWizard checkoutWizard = new CheckoutWizard(project);
    if (!checkoutWizard.showAndGet()) {
      return;
    }
    final boolean useAlternateCheckoutPath = checkoutWizard.useAlternativeCheckoutLocation();
    final File checkoutDirectory = checkoutWizard.getCheckoutDirectory();

    final CvsElement[] selectedElements = checkoutWizard.getSelectedElements();
    final CvsHandler checkoutHandler = CommandCvsHandler.createCheckoutHandler(
      checkoutWizard.getConfigurationWithDateOrRevisionSettings(),
      collectCheckoutPaths(selectedElements),
      checkoutDirectory,
      useAlternateCheckoutPath,
      CvsApplicationLevelConfiguration.getInstance().MAKE_CHECKED_OUT_FILES_READONLY,
      VcsConfiguration.getInstance(project).getCheckoutOption());

    final CvsOperationExecutor executor = new CvsOperationExecutor(null);
    executor.performActionSync(checkoutHandler, new CvsOperationExecutorCallback() {
      public void executionFinished(boolean successfully) {
        if (!executor.hasNoErrors()) {
          Messages.showErrorDialog(CvsBundle.message("message.error.checkout", executor.getResult().composeError().getLocalizedMessage()),
                                   CvsBundle.message("operation.name.check.out.project"));
        }

        refreshAfterCheckout(listener, selectedElements, checkoutDirectory, useAlternateCheckoutPath);
      }

      public void executionFinishedSuccessfully() {
      }

      public void executeInProgressAfterAction(ModalityContext modaityContext) {
      }
    });
  }

  public void refreshAfterCheckout(final Listener listener, final CvsElement[] selectedElements, final File checkoutDirectory,
                                   final boolean useAlternateCheckoutPath) {

    VirtualFileManager.getInstance().asyncRefresh(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            for (CvsElement element : selectedElements) {
              final File path = useAlternateCheckoutPath ? checkoutDirectory : new File(checkoutDirectory, element.getCheckoutPath());
              listener.directoryCheckedOut(path, CvsVcs2.getKey());
            }
            listener.checkoutCompleted();
          }
        });
      }
    });
  }

  private static String[] collectCheckoutPaths(final CvsElement[] mySelectedElements) {
    final String[] checkoutPaths = new String[mySelectedElements.length];
    for (int i = 0; i < mySelectedElements.length; i++) {
      final CvsElement selectedElement = mySelectedElements[i];
      checkoutPaths[i] = selectedElement.getCheckoutPath();
    }
    return checkoutPaths;
  }

  public String getVcsName() {
    return "_CVS";
  }
}
