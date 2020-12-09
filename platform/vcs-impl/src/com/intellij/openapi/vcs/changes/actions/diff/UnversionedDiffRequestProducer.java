// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.diff.DiffBundle;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class UnversionedDiffRequestProducer implements ChangeDiffRequestChain.Producer {
  @Nullable private final Project myProject;
  @NotNull private final FilePath myPath;
  @NotNull private final ChangesBrowserNode.Tag myTag;

  private UnversionedDiffRequestProducer(@Nullable Project project, @NotNull FilePath path,
                                         @NotNull ChangesBrowserNode.Tag tag) {
    myProject = project;
    myPath = path;
    myTag = tag;
  }

  @NotNull
  @Override
  public FilePath getFilePath() {
    return myPath;
  }

  @NotNull
  @Override
  public FileStatus getFileStatus() {
    return FileStatus.UNKNOWN;
  }

  @Override
  public ChangesBrowserNode.Tag getPopupTag() {
    return myTag;
  }

  @NotNull
  @Override
  public String getName() {
    return myPath.getPresentableUrl();
  }

  @NotNull
  @Override
  public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException, ProcessCanceledException {
    VirtualFile file = myPath.getVirtualFile();
    if (file == null) throw new DiffRequestProducerException(DiffBundle.message("error.cant.show.diff.file.not.found"));
    return createRequest(myProject, file);
  }


  @NotNull
  public static UnversionedDiffRequestProducer create(@Nullable Project project, @NotNull FilePath path) {
    return create(project, path, ChangesBrowserNode.UNVERSIONED_FILES_TAG);
  }

  @NotNull
  public static UnversionedDiffRequestProducer create(@Nullable Project project, @NotNull FilePath path,
                                                      @NotNull ChangesBrowserNode.Tag tag) {
    return new UnversionedDiffRequestProducer(project, path, tag);
  }

  @NotNull
  private static DiffRequest createRequest(@Nullable Project project, @NotNull VirtualFile file) {
    DiffContentFactory contentFactory = DiffContentFactory.getInstance();
    DiffContent content1 = contentFactory.createEmpty();
    DiffContent content2 = contentFactory.create(project, file);

    SimpleDiffRequest request = new SimpleDiffRequest(DiffRequestFactory.getInstance().getTitle(file), content1, content2,
                                                      null, ChangeDiffRequestProducer.getYourVersion());

    DiffUtil.putDataKey(request, VcsDataKeys.CURRENT_UNVERSIONED, file);
    return request;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    UnversionedDiffRequestProducer producer = (UnversionedDiffRequestProducer)o;
    return Objects.equals(myPath, producer.myPath);
  }

  @Override
  public int hashCode() {
    return myPath.hashCode();
  }
}
