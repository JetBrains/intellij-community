/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.history;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Date;

public class ComparableVcsRevisionOnOperation implements VcsFileRevision {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.history.ComparableVcsRevisionOnOperation");

  private final GetFileContentOperation myOperation;
  private final Project myProject;

  public ComparableVcsRevisionOnOperation(GetFileContentOperation operation, Project project) {
    myOperation = operation;
    myProject = project;
  }

  public boolean isDeleted() {
    return myOperation.isDeleted();
  }

  public byte[] getContent() {
    LOG.assertTrue(myOperation.isLoaded());
    return myOperation.getFileBytes();
  }

  public byte[] loadContent() throws IOException, VcsException {
    if (!myOperation.isLoaded()) {
      CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
      executor.performActionSync(new CommandCvsHandler(CvsBundle.message("operation.name.load.file"), myOperation),
                                 CvsOperationExecutorCallback.EMPTY);
      CvsResult result = executor.getResult();
      if (result.isCanceled()) {
        throw new ProcessCanceledException();
      }
      if (result.hasErrors()) {
        throw result.composeError();
      }
    }
    return getContent();
  }

  public boolean fileNotFound() {
    return myOperation.fileNotFound();
  }

  public CvsRevisionNumber getRevision() {
    return myOperation.getRevisionNumber();
  }

  public boolean isLoaded() {
    return myOperation.isLoaded();
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return getRevision();
  }

  public Date getRevisionDate() {
    return null;
  }

  public String getAuthor() {
    return null;
  }

  public String getCommitMessage() {
    return null;
  }

  public String getBranchName() {
    return null;
  }

  @Nullable
  @Override
  public RepositoryLocation getChangedRepositoryPath() {
    return null;
  }
}
