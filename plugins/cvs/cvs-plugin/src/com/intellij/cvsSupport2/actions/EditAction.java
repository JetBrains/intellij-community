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
import org.jetbrains.annotations.NotNull;

/**
 * author: lesya
 */
public class EditAction extends AbstractActionFromEditGroup {
  public EditAction() {
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (!e.getPresentation().isVisible()) {
      return;
    }
    Project project = CvsContextWrapper.createInstance(e).getProject();

    if (project == null) return;

    adjustName(CvsVcs2.getInstance(project).getEditOptions().getValue(), e);
  }


  @Override
  protected String getTitle(VcsContext context) {
    return CvsBundle.message("action.name.edit");
  }

  @Override
  protected CvsHandler getCvsHandler(CvsContext context) {
    Project project = context.getProject();
    if (CvsVcs2.getInstance(project).getEditOptions().getValue()
        || OptionsDialog.shiftIsPressed(context.getModifiers())) {
      EditOptionsDialog editOptionsDialog = new EditOptionsDialog(project);
      if (!editOptionsDialog.showAndGet()) {
        return CvsHandler.NULL;
      }
    }

    return CommandCvsHandler.createEditHandler(context.getSelectedFiles(),
                                               CvsConfiguration.getInstance(project).RESERVED_EDIT);
  }
}
