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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
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
    return CvsBundle.message("operation.name.check.out.project");
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    final Project project = context.getProject();
    CheckoutWizard checkoutWizard = new CheckoutWizard(project);
    if (!checkoutWizard.showAndGet()) {
      return CvsHandler.NULL;
    }
    myUseAlternativeCheckoutPath = checkoutWizard.useAlternativeCheckoutLocation();
    myCheckoutDirectory = checkoutWizard.getCheckoutDirectory();

    mySelectedElements = checkoutWizard.getSelectedElements();
    return CommandCvsHandler.createCheckoutHandler(
      checkoutWizard.getSelectedConfiguration(),
      collectCheckoutPaths(),
      myCheckoutDirectory,
      myUseAlternativeCheckoutPath,
      CvsApplicationLevelConfiguration.getInstance().MAKE_CHECKED_OUT_FILES_READONLY,
      project == null ? null : VcsConfiguration.getInstance(project).getCheckoutOption());
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
