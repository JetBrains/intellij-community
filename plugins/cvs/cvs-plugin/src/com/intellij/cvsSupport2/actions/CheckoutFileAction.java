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
package com.intellij.cvsSupport2.actions;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsCheckOut.ui.CheckoutFileDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.ui.ReplaceFileConfirmationDialog;
import com.intellij.util.ui.OptionsDialog;
import org.jetbrains.annotations.NotNull;

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

  @Override
  protected String getTitle(VcsContext context) {
    return CvsBundle.getCheckoutOperationName();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (!e.getPresentation().isVisible()) {
      return;
    }
    Project project = CvsContextWrapper.createInstance(e).getProject();
    if (project == null) return;
    adjustName(CvsVcs2.getInstance(project).getCheckoutOptions().getValue(), e);
  }

  @Override
  protected CvsHandler getCvsHandler(CvsContext context) {
    if (myModifiedFiles != null) {
      if (!myModifiedFiles.isEmpty()) {
        if (!new ReplaceFileConfirmationDialog(context.getProject(), CvsBundle.getCheckoutOperationName()).requestConfirmation(myModifiedFiles)) {
          return CvsHandler.NULL;
        }

      }
    }

    myModifiedFiles = null;

    Project project = context.getProject();
    FilePath[] filesArray = context.getSelectedFilePaths();
    List<FilePath> files = Arrays.asList(filesArray);
    if (CvsVcs2.getInstance(project).getCheckoutOptions().getValue() || OptionsDialog.shiftIsPressed(context.getModifiers())) {
      CheckoutFileDialog checkoutFileDialog = new CheckoutFileDialog(project, files);
      if (!checkoutFileDialog.showAndGet()) {
        return CvsHandler.NULL;
      }
    }

    return CommandCvsHandler.createCheckoutFileHandler(filesArray, CvsConfiguration.getInstance(project),
                                                       VcsConfiguration.getInstance(project).getCheckoutOption());
  }

  @Override
  protected void beforeActionPerformed(VcsContext context) {
    super.beforeActionPerformed(context);
    myModifiedFiles =
      new ReplaceFileConfirmationDialog(context.getProject(), CvsBundle.getCheckoutOperationName()).collectModifiedFiles(context.getSelectedFiles());
  }
}
