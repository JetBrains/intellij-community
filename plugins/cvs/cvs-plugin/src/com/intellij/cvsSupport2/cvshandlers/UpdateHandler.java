/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.update.UpdateSettings;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsoperations.common.PostCvsActivity;
import com.intellij.cvsSupport2.cvsoperations.cvsUpdate.MergedWithConflictProjectOrModuleFile;
import com.intellij.cvsSupport2.cvsoperations.cvsUpdate.UpdateOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsUpdate.ui.CorruptedProjectFilesDialog;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.util.Options;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * author: lesya
 */
public class UpdateHandler extends CommandCvsHandler implements PostCvsActivity {
  private final FilePath[] myFiles;

  private final Project myProject;
  private final Collection<MergedWithConflictProjectOrModuleFile> myCorruptedFiles = new ArrayList<>();
  private final UpdatedFiles myUpdatedFiles;
  private final UpdateSettings myUpdateSettings;

  public UpdateHandler(FilePath[] files, UpdateSettings updateSettings, @NotNull Project project, @NotNull UpdatedFiles updatedFiles) {
    super(CvsBundle.message("operation.name.update"), new UpdateOperation(files, updateSettings, project),
          FileSetToBeUpdated.selectedFiles(files));
    myFiles = files;
    myProject = project;
    myUpdatedFiles = updatedFiles;
    myUpdateSettings = updateSettings;
  }

  public void registerCorruptedProjectOrModuleFile(MergedWithConflictProjectOrModuleFile mergedWithConflictProjectOrModuleFile) {
    myCorruptedFiles.add(mergedWithConflictProjectOrModuleFile);
  }

  protected void onOperationFinished(ModalityContext modalityContext) {
    if (myUpdateSettings.getPruneEmptyDirectories()) {
      final IOFilesBasedDirectoryPruner pruner = new IOFilesBasedDirectoryPruner(ProgressManager.getInstance().getProgressIndicator());
      for (FilePath file : myFiles) {
        pruner.addFile(file.getIOFile());
      }
      pruner.execute();
    }

    if (!myCorruptedFiles.isEmpty()) {
      final int showOptions = CvsConfiguration.getInstance(myProject).SHOW_CORRUPTED_PROJECT_FILES;

      if (showOptions == Options.PERFORM_ACTION_AUTOMATICALLY) {
        for (final MergedWithConflictProjectOrModuleFile myCorruptedFile : myCorruptedFiles) {
          myCorruptedFile.setShouldBeCheckedOut();
        }
      }
      else if (showOptions == Options.SHOW_DIALOG){
        modalityContext.runInDispatchThread(() -> new CorruptedProjectFilesDialog(myProject, myCorruptedFiles).show(), myProject);
      }

      final VcsKey vcsKey = CvsVcs2.getKey();
      for (final MergedWithConflictProjectOrModuleFile myCorruptedFile : myCorruptedFiles) {
        if (myCorruptedFile.shouldBeCheckedOut()) {
          addFileToCheckout(myCorruptedFile.getOriginal());
        }
        else {
          myUpdatedFiles.getGroupById(FileGroup.MODIFIED_ID).add(myCorruptedFile.getOriginal().getPath(), vcsKey, null);
        }
      }
    }
  }

  protected PostCvsActivity getPostActivityHandler() {
    return this;
  }
}
