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
package com.intellij.cvsSupport2;

import com.intellij.cvsSupport2.actions.update.UpdateSettings;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsUpdatePolicy;
import com.intellij.cvsSupport2.cvshandlers.UpdateHandler;
import com.intellij.cvsSupport2.updateinfo.UpdatedFilesProcessor;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.update.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class CvsStatusEnvironment implements UpdateEnvironment {
  private final Project myProject;

  public CvsStatusEnvironment(Project project) {
    myProject = project;

  }

  @Override
  public void fillGroups(UpdatedFiles updatedFiles) {
    CvsUpdatePolicy.fillGroups(updatedFiles);
  }

  @Override
  @NotNull
  public UpdateSession updateDirectories(FilePath @NotNull [] contentRoots, final UpdatedFiles updatedFiles,
                                         ProgressIndicator progressIndicator,
                                         @NotNull final Ref<SequentialUpdatesContext> context) {
    final UpdateSettings updateSettings = UpdateSettings.DONT_MAKE_ANY_CHANGES;
    final UpdateHandler handler = CommandCvsHandler.createUpdateHandler(contentRoots,
                                                                        updateSettings, myProject, updatedFiles);
    handler.addCvsListener(new UpdatedFilesProcessor(updatedFiles));
    CvsOperationExecutor cvsOperationExecutor = new CvsOperationExecutor(true, myProject, ModalityState.defaultModalityState());
    cvsOperationExecutor.setShowErrors(false);
    cvsOperationExecutor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    final CvsResult result = cvsOperationExecutor.getResult();
    return new UpdateSessionAdapter(result.getErrorsAndWarnings(), result.isCanceled());
  }

  @Override
  public Configurable createConfigurable(Collection<FilePath> files) {
    return null;
  }

  @Override
  public boolean validateOptions(final Collection<FilePath> roots) {
    return true;
  }
}
