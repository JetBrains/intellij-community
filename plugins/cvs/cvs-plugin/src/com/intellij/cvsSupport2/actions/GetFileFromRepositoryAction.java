/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.cvsSupport2.actions.cvsContext.CvsLightweightFile;
import com.intellij.cvsSupport2.actions.update.UpdateSettingsOnCvsConfiguration;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.update.UpdatedFiles;

public class GetFileFromRepositoryAction extends ActionOnSelectedElement{
  public GetFileFromRepositoryAction() {
    super(true);
    CvsActionVisibility visibility = getVisibility();
    visibility.canBePerformedOnSeveralFiles();
    visibility.canBePerformedOnCvsLightweightFile();
    visibility.shouldNotBePerformedOnDirectory();
  }

  @Override
  protected String getTitle(VcsContext context) {
    return CvsBundle.message("action.name.get.file.from.repository");
  }

  @Override
  protected CvsHandler getCvsHandler(CvsContext context) {
    CvsLightweightFile[] cvsLightweightFiles = context.getSelectedLightweightFiles();
    Project project = context.getProject();
    if (cvsLightweightFiles != null) {
      boolean makeNewFilesReadOnly = project != null && CvsConfiguration.getInstance(project).MAKE_NEW_FILES_READONLY;
      return CommandCvsHandler.createGetFileFromRepositoryHandler(cvsLightweightFiles, makeNewFilesReadOnly);
    }
    final FilePath[] filePaths = context.getSelectedFilePaths();
    if (filePaths != null) {
      CvsConfiguration cvsConfiguration = CvsConfiguration.getInstance(project);
      // do not use -j's
      final UpdateSettingsOnCvsConfiguration updateSettings = new UpdateSettingsOnCvsConfiguration(cvsConfiguration,
                                                                                                   cvsConfiguration.CLEAN_COPY,
                                                                                                   cvsConfiguration.RESET_STICKY);
      return CommandCvsHandler.createUpdateHandler(filePaths, updateSettings, project, UpdatedFiles.create());
    }
    return CvsHandler.NULL;
  }
}