/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class UnversionedDiffRequestProducer implements ChangeDiffRequestChain.Producer {
  @Nullable private final Project myProject;
  @NotNull private final VirtualFile myFile;

  private UnversionedDiffRequestProducer(@Nullable Project project, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  @Override
  public FilePath getFilePath() {
    return VcsUtil.getFilePath(myFile);
  }

  @NotNull
  @Override
  public FileStatus getFileStatus() {
    return FileStatus.UNKNOWN;
  }

  @Nullable
  @Override
  public Object getPopupTag() {
    return ChangesBrowserNode.UNVERSIONED_FILES_TAG;
  }

  @NotNull
  @Override
  public String getName() {
    return myFile.getPresentableUrl();
  }

  @NotNull
  @Override
  public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException, ProcessCanceledException {
    if (!myFile.isValid()) throw new DiffRequestProducerException("Can't show diff - file not found");
    return createRequest(myProject, myFile);
  }


  @NotNull
  public static UnversionedDiffRequestProducer create(@Nullable Project project, @NotNull VirtualFile file) {
    return new UnversionedDiffRequestProducer(project, file);
  }

  @NotNull
  private static DiffRequest createRequest(@Nullable Project project, @NotNull VirtualFile file) {
    DiffContentFactory contentFactory = DiffContentFactory.getInstance();
    DiffContent content1 = contentFactory.createEmpty();
    DiffContent content2 = contentFactory.create(project, file);

    SimpleDiffRequest request = new SimpleDiffRequest(DiffRequestFactory.getInstance().getTitle(file), content1, content2,
                                                      null, ChangeDiffRequestProducer.YOUR_VERSION);

    DiffUtil.putDataKey(request, VcsDataKeys.CURRENT_UNVERSIONED, file);
    return request;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    UnversionedDiffRequestProducer producer = (UnversionedDiffRequestProducer)o;
    return Objects.equals(myFile, producer.myFile);
  }

  @Override
  public int hashCode() {
    return myFile.hashCode();
  }
}
