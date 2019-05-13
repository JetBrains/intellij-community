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
import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.DeleteTagDialog;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContext;

/**
 * author: lesya
 */
public class DeleteTagAction extends ActionOnSelectedElement{

  public DeleteTagAction() {
    super(false);
    final CvsActionVisibility visibility = getVisibility();
    visibility.canBePerformedOnSeveralFiles();
    visibility.addCondition(FILES_EXIST_IN_CVS);
  }

  @Override
  protected String getTitle(VcsContext context) {
    return CvsBundle.message("action.name.delete.tag");
  }

  @Override
  protected CvsHandler getCvsHandler(CvsContext context) {
    final FilePath[] selectedFiles = context.getSelectedFilePaths();
    final DeleteTagDialog deleteTagDialog = new DeleteTagDialog(selectedFiles, context.getProject());
    if (!deleteTagDialog.showAndGet()) {
      return CvsHandler.NULL;
    }

    return CommandCvsHandler.createRemoveTagAction(selectedFiles, deleteTagDialog.getTagName());
  }
}
