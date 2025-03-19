// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.DiffEditorTitleCustomizer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.impl.DiffEditorTitleDetails;
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
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class UnversionedDiffRequestProducer implements ChangeDiffRequestChain.Producer {
  private final @Nullable Project myProject;
  private final @NotNull FilePath myPath;
  private final @NotNull ChangesBrowserNode.Tag myTag;

  private UnversionedDiffRequestProducer(@Nullable Project project, @NotNull FilePath path,
                                         @NotNull ChangesBrowserNode.Tag tag) {
    myProject = project;
    myPath = path;
    myTag = tag;
  }

  @Override
  public @NotNull FilePath getFilePath() {
    return myPath;
  }

  @Override
  public @NotNull FileStatus getFileStatus() {
    return FileStatus.UNKNOWN;
  }

  @Override
  public @NotNull ChangesBrowserNode.Tag getTag() {
    return myTag;
  }

  @Override
  public @NotNull String getName() {
    return myPath.getPresentableUrl();
  }

  @Override
  public @NotNull DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException, ProcessCanceledException {
    VirtualFile file = myPath.getVirtualFile();
    if (file == null) throw new DiffRequestProducerException(DiffBundle.message("error.cant.show.diff.file.not.found"));
    return createRequest(myProject, file);
  }


  public static @NotNull UnversionedDiffRequestProducer create(@Nullable Project project, @NotNull FilePath path) {
    return create(project, path, ChangesBrowserNode.UNVERSIONED_FILES_TAG);
  }

  public static @NotNull UnversionedDiffRequestProducer create(@Nullable Project project, @NotNull FilePath path,
                                                               @NotNull ChangesBrowserNode.Tag tag) {
    return new UnversionedDiffRequestProducer(project, path, tag);
  }

  private static @NotNull DiffRequest createRequest(@Nullable Project project, @NotNull VirtualFile file) {
    DiffContentFactory contentFactory = DiffContentFactory.getInstance();
    DiffContent content1 = contentFactory.createEmpty();
    DiffContent content2 = contentFactory.create(project, file);

    String title2 = DiffBundle.message("merge.version.title.current");
    SimpleDiffRequest request = new SimpleDiffRequest(DiffRequestFactory.getInstance().getTitle(file), content1, content2, null, title2);

    DiffUtil.putDataKey(request, VcsDataKeys.CURRENT_UNVERSIONED, file);
    DiffUtil.addTitleCustomizers(request,
                                 DiffEditorTitleCustomizer.EMPTY,
                                 DiffEditorTitleDetails.create(project, VcsUtil.getFilePath(file), title2).getCustomizer());
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
