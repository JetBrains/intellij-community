// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.chooseNotNull;

class ShelvedWrapper {
  @Nullable private final ShelvedChange myShelvedChange;
  @Nullable private final ShelvedBinaryFile myBinaryFile;

  ShelvedWrapper(@NotNull ShelvedChange shelvedChange) {
    myShelvedChange = shelvedChange;
    myBinaryFile = null;
  }

  ShelvedWrapper(@NotNull ShelvedBinaryFile binaryFile) {
    myShelvedChange = null;
    myBinaryFile = binaryFile;
  }

  @Nullable
  public ShelvedChange getShelvedChange() {
    return myShelvedChange;
  }

  @Nullable
  public ShelvedBinaryFile getBinaryFile() {
    return myBinaryFile;
  }

  public  String getPath() {
    return chooseNotNull(getAfterPath(), getBeforePath());
  }

  @NlsSafe
  public String getRequestName() {
    return FileUtil.toSystemDependentName(getPath());
  }

  String getBeforePath() {
    return myShelvedChange != null ? myShelvedChange.getBeforePath() : Objects.requireNonNull(myBinaryFile).BEFORE_PATH;
  }

  String getAfterPath() {
    return myShelvedChange != null ? myShelvedChange.getAfterPath() : Objects.requireNonNull(myBinaryFile).AFTER_PATH;
  }

  FileStatus getFileStatus() {
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

}
