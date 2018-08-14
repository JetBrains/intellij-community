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
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.CreateTagDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContext;

/**
 * author: lesya
 */
public class CreateTagAction extends ActionOnSelectedElement{

  public CreateTagAction() {
    super(true);
    final CvsActionVisibility visibility = getVisibility();
    visibility.canBePerformedOnSeveralFiles();
    visibility.canBePerformedOnLocallyDeletedFile();
    visibility.addCondition(FILES_EXIST_IN_CVS);
  }

  @Override
  protected String getTitle(VcsContext context) {
    return CvsBundle.message("operation.name.create.tag");
  }

  @Override
  protected CvsHandler getCvsHandler(CvsContext context) {
    final FilePath[] selectedFiles = context.getSelectedFilePaths();
    final Project project = context.getProject();
    final CreateTagDialog dialog = new CreateTagDialog(selectedFiles, project, true);
    if (!dialog.showAndGet()) {
      return CvsHandler.NULL;
    }

    final boolean makeNewFilesReadOnly = CvsConfiguration.getInstance(project).MAKE_NEW_FILES_READONLY;
    return CommandCvsHandler.createTagHandler(
      selectedFiles, dialog.getTagName(), dialog.switchToThisBranch(), dialog.getOverrideExisting(), makeNewFilesReadOnly, project);
  }
}
