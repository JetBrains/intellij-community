// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.chooseNotNull;

class ShelvedWrapper extends ChangeViewDiffRequestProcessor.Wrapper {
  @Nullable private final ShelvedChange myShelvedChange;
  @Nullable private final ShelvedBinaryFile myBinaryFile;
  @NotNull private final ShelvedChangeList myChangeList;

  ShelvedWrapper(@NotNull ShelvedChange shelvedChange, @NotNull ShelvedChangeList changeList) {
    myShelvedChange = shelvedChange;
    myBinaryFile = null;
    myChangeList = changeList;
  }

  ShelvedWrapper(@NotNull ShelvedBinaryFile binaryFile, @NotNull ShelvedChangeList changeList) {
    myShelvedChange = null;
    myBinaryFile = binaryFile;
    myChangeList = changeList;
  }

  @NotNull
  public ShelvedChangeList getChangeList() {
    return myChangeList;
  }

  @Override
  public @NotNull Object getUserObject() {
    return myShelvedChange != null ? myShelvedChange : Objects.requireNonNull(myBinaryFile);
  }

  @Nullable
  @Override
  public ChangesBrowserNode.Tag getTag() {
    return new ShelvedListTag(myChangeList);
  }

  @Nullable
  public ShelvedChange getShelvedChange() {
    return myShelvedChange;
  }

  @Nullable
  public ShelvedBinaryFile getBinaryFile() {
    return myBinaryFile;
  }

  @NotNull
  public String getPath() {
    return chooseNotNull(getAfterPath(), getBeforePath());
  }

  @NlsSafe
  public String getRequestName() {
    return FileUtil.toSystemDependentName(getPath());
  }

  @NlsSafe
  @NotNull
  @Override
  public String getPresentableName() {
    if (myShelvedChange == null) {
      return getRequestName();
    }

    return ChangesUtil.getFilePath(myShelvedChange.getChange()).getName();
  }

  String getBeforePath() {
    return myShelvedChange != null ? myShelvedChange.getBeforePath() : Objects.requireNonNull(myBinaryFile).BEFORE_PATH;
  }

  String getAfterPath() {
    return myShelvedChange != null ? myShelvedChange.getAfterPath() : Objects.requireNonNull(myBinaryFile).AFTER_PATH;
  }

  @Override
  public @NotNull FilePath getFilePath() {
    Change change = myShelvedChange != null ? myShelvedChange.getChange() : null;
    return change != null ? ChangesUtil.getFilePath(change) : VcsUtil.getFilePath(getPath());
  }

  @Override
  @NotNull
  public FileStatus getFileStatus() {
    return myShelvedChange != null ? myShelvedChange.getFileStatus() : Objects.requireNonNull(myBinaryFile).getFileStatus();
  }

  Change getChange(@NotNull Project project) {
    return myShelvedChange != null ? myShelvedChange.getChange() : Objects.requireNonNull(myBinaryFile).createChange(project);
  }

  @Nullable
  public VirtualFile getBeforeVFUnderProject(@NotNull final Project project) {
    if (getBeforePath() == null || project.getBasePath() == null) return null;
    final File baseDir = new File(project.getBasePath());
    final File file = new File(baseDir, getBeforePath());
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
  }

  @Override
  public @Nullable DiffRequestProducer createProducer(@Nullable Project project) {
    if (project == null) return null;
    return new ShelvedWrapperDiffRequestProducer(project, this);
  }

  public static class ShelvedListTag extends ChangesBrowserNode.ValueTag<ShelvedChangeList> {
    public ShelvedListTag(@NotNull ShelvedChangeList value) {
      super(value);
    }

    @Nls
    @Override
    public String toString() {
      return value.DESCRIPTION;
    }
  }
}
