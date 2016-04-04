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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.11.2006
 * Time: 20:54:15
 */
package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class CvsContentRevision implements ByteBackedContentRevision {
  protected final RevisionOrDate myRevision;
  protected final File myFile;
  private final FilePath myLocalFile;
  private final CvsEnvironment myEnvironment;
  private final Project myProject;

  private byte[] myContent;

  public CvsContentRevision(final File file,
                            final File localFile,
                            final RevisionOrDate revision,
                            final CvsEnvironment environment,
                            final Project project) {
    myFile = file;
    myLocalFile = VcsContextFactory.SERVICE.getInstance().createFilePathOn(localFile);
    myRevision = revision;
    myEnvironment = environment;
    myProject = project;
  }

  @Nullable
  public String getContent() throws VcsException {
    byte[] content = getContentAsBytes();
    return content == null ? null : CharsetToolkit.bytesToString(content, myLocalFile.getCharset());
  }

  @Nullable
  @Override
  public byte[] getContentAsBytes() throws VcsException {
    if (myContent == null) {
      final GetFileContentOperation operation = new GetFileContentOperation(myFile, myEnvironment, myRevision);
      CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
      executor.performActionSync(new CommandCvsHandler(CvsBundle.message("operation.name.load.file"),
                                                       operation),
                                 CvsOperationExecutorCallback.EMPTY);
      CvsResult result = executor.getResult();
      if (result.isCanceled()) {
        throw new ProcessCanceledException();
      }
      if (result.hasErrors()) {
        throw result.composeError();
      }
      if (!operation.isLoaded()) {
        throw new VcsException("Network problem");
      }

      myContent = operation.getFileBytes();
    }
    return myContent;
  }

  @NotNull
  public FilePath getFile() {
    return myLocalFile;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return myRevision.getCvsRevisionNumber();
  }

  @Override @NonNls
  public String toString() {
    return "CvsContentRevision:" + myFile + "@" + myRevision;
  }
}
