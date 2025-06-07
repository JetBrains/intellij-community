// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesProvider;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.chooseNotNull;

@ApiStatus.Internal
public class ShelvedWrapper extends ChangeViewDiffRequestProcessor.Wrapper implements SavedPatchesProvider.ChangeObject {
  private final @Nullable ShelvedChange myShelvedChange;
  private final @Nullable ShelvedBinaryFile myBinaryFile;
  private final @NotNull ShelvedChangeList myChangeList;

  public ShelvedWrapper(@Nullable ShelvedChange shelvedChange,
                        @Nullable ShelvedBinaryFile binaryFile,
                        @NotNull ShelvedChangeList changeList) {
    myShelvedChange = shelvedChange;
    myBinaryFile = binaryFile;
    myChangeList = changeList;
  }

  public ShelvedWrapper(@NotNull ShelvedChange shelvedChange, @NotNull ShelvedChangeList changeList) {
    this(shelvedChange, null, changeList);
  }

  public ShelvedWrapper(@NotNull ShelvedBinaryFile binaryFile, @NotNull ShelvedChangeList changeList) {
    this(null, binaryFile, changeList);
  }

  public @NotNull ShelvedChangeList getChangeList() {
    return myChangeList;
  }

  @Override
  public @NotNull Object getUserObject() {
    return myShelvedChange != null ? myShelvedChange : Objects.requireNonNull(myBinaryFile);
  }

  @Override
  public @Nullable ChangesBrowserNode.Tag getTag() {
    return new ShelvedListTag(myChangeList);
  }

  public @Nullable ShelvedChange getShelvedChange() {
    return myShelvedChange;
  }

  public @Nullable ShelvedBinaryFile getBinaryFile() {
    return myBinaryFile;
  }

  public @NotNull String getPath() {
    return chooseNotNull(getAfterPath(), getBeforePath());
  }

  @Override
  public @Nullable FilePath getOriginalFilePath() {
    ContentRevision beforeRevision = myShelvedChange != null ? myShelvedChange.getChange().getBeforeRevision() : null;
    if (beforeRevision != null) {
      return beforeRevision.getFile();
    }
    String beforePath = getBeforePath();
    if (beforePath == null) return null;
    return VcsUtil.getFilePath(beforePath, false);
  }

  public @NlsSafe String getRequestName() {
    return FileUtil.toSystemDependentName(getPath());
  }

  @Override
  public @NotNull @Nls String getPresentableName() {
    if (myShelvedChange == null) {
      return getRequestName();
    }

    return ChangesUtil.getFilePath(myShelvedChange.getChange()).getName();
  }

  public String getBeforePath() {
    return myShelvedChange != null ? myShelvedChange.getBeforePath() : Objects.requireNonNull(myBinaryFile).BEFORE_PATH;
  }

  String getAfterPath() {
    return myShelvedChange != null ? myShelvedChange.getAfterPath() : Objects.requireNonNull(myBinaryFile).AFTER_PATH;
  }

  @Override
  public @NotNull FilePath getFilePath() {
    Change change = myShelvedChange != null ? myShelvedChange.getChange() : null;
    return change != null ? ChangesUtil.getFilePath(change) : VcsUtil.getFilePath(getPath(), false);
  }

  @Override
  public @NotNull FileStatus getFileStatus() {
    return myShelvedChange != null ? myShelvedChange.getFileStatus() : Objects.requireNonNull(myBinaryFile).getFileStatus();
  }

  @ApiStatus.Internal
  public Change getChangeWithLocal(@NotNull Project project) {
    return myShelvedChange != null ? myShelvedChange.getChange() : Objects.requireNonNull(myBinaryFile).createChange(project);
  }

  public @Nullable VirtualFile getBeforeVFUnderProject(final @NotNull Project project) {
    if (getBeforePath() == null || project.getBasePath() == null) return null;
    final File baseDir = new File(project.getBasePath());
    final File file = new File(baseDir, getBeforePath());
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
  }

  @Override
  public @Nullable ChangeDiffRequestChain.Producer createProducer(@Nullable Project project) {
    if (project == null) return null;
    return new ShelvedWrapperDiffRequestProducer(project, this);
  }

  @Override
  public @Nullable ChangeDiffRequestChain.Producer createDiffRequestProducer(@Nullable Project project) {
    return createProducer(project);
  }

  @Override
  public @Nullable ChangeDiffRequestChain.Producer createDiffWithLocalRequestProducer(@Nullable Project project, boolean useBeforeVersion) {
    if (useBeforeVersion || project == null) return null;
    return DiffShelvedChangesActionProvider.createDiffProducer(project, this, true);
  }

  public static class ShelvedListTag extends ChangesBrowserNode.ValueTag<ShelvedChangeList> {
    public ShelvedListTag(@NotNull ShelvedChangeList value) {
      super(value);
    }

    @Override
    public @Nls String toString() {
      return value.getDescription();
    }
  }
}
