/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.patch;

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

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;


public abstract class CommonBinaryFilePatchInProgress<T extends FilePatch> extends AbstractFilePatchInProgress<T> {


  protected CommonBinaryFilePatchInProgress(T patch,
                                            Collection<VirtualFile> autoBases,
                                            VirtualFile baseDir) {
    super(patch, autoBases, baseDir);
  }

  @Override
  public ContentRevision getNewContentRevision() {
    if (FilePatchStatus.DELETED.equals(myStatus)) return null;

    if (myNewContentRevision != null) return myNewContentRevision;
    if (myPatch.getAfterFileName() != null) {
      FilePath newFilePath = getFilePath();
      myNewContentRevision = createNewContentRevision(newFilePath);
    }
    return myNewContentRevision;
  }

  @NotNull
  protected abstract BinaryContentRevision createNewContentRevision(@NotNull final FilePath newFilePath);

  @NotNull
  protected abstract Change createChange(Project project);

  @NotNull
  protected FilePath getFilePath() {
    return FilePatchStatus.ADDED.equals(myStatus) ? VcsUtil.getFilePath(myIoCurrentBase, false)
                                                  : detectNewFilePathForMovedOrModified();
  }

  @NotNull
  @Override
  public DiffRequestProducer getDiffRequestProducers(final Project project, final PatchReader baseContents) {
    return new DiffRequestProducer() {
      @NotNull
      @Override
      public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
        throws DiffRequestProducerException, ProcessCanceledException {
        return PatchDiffRequestFactory.createDiffRequest(project, createChange(project), getName(), context, indicator);
      }

      @NotNull
      @Override
      public String getName() {
        final File file1 = new File(VfsUtilCore.virtualToIoFile(getBase()),
                                    myPatch.getAfterName() == null ? myPatch.getBeforeName() : myPatch.getAfterName());
        return FileUtil.toSystemDependentName(file1.getPath());
      }
    };
  }
}
