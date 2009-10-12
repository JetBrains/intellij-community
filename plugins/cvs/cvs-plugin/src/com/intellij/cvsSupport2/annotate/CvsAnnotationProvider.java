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
package com.intellij.cvsSupport2.annotate;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.AnnotateOperation;
import com.intellij.cvsSupport2.history.CvsHistoryProvider;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.List;

public class CvsAnnotationProvider implements AnnotationProvider{
  private final Project myProject;

  public CvsAnnotationProvider(final Project project) {
    myProject = project;
  }

  public FileAnnotation annotate(VirtualFile file) throws VcsException {
    final AnnotateOperation operation = AnnotateOperation.createForFile(new File(file.getPath()));
    final CvsOperationExecutor executor = new CvsOperationExecutor(true, myProject, ModalityState.defaultModalityState());
    executor.performActionSync(new CommandCvsHandler(CvsBundle.getAnnotateOperationName(), operation),
                               CvsOperationExecutorCallback.EMPTY);
    if (executor.getResult().hasNoErrors()) {
      final CvsHistoryProvider historyProvider = (CvsHistoryProvider) CvsVcs2.getInstance(myProject).getVcsHistoryProvider();
      final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(file);
      final List<VcsFileRevision> revisions = historyProvider.createRevisions(filePath);
      return new CvsFileAnnotation(operation.getContent(), operation.getLineAnnotations(), revisions, file);
    } else {
      throw executor.getFirstError();
    }
  }

  public FileAnnotation annotate(VirtualFile file, VcsFileRevision revision) throws VcsException {
    final CvsConnectionSettings settings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(file.getParent());
    return CvsVcs2.getInstance(myProject).createAnnotation(file, revision.getRevisionNumber().asString(), settings);
  }

  public boolean isAnnotationValid( VcsFileRevision rev ){
    return true;
  }
}
