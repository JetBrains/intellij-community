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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.util.ObjectUtils.assertNotNull;
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

  public String getRequestName() {
    return FileUtil.toSystemDependentName(chooseNotNull(getAfterPath(), getBeforePath()));
  }

  String getBeforePath() {
    return myShelvedChange != null ? myShelvedChange.getBeforePath() : assertNotNull(myBinaryFile).BEFORE_PATH;
  }

  String getAfterPath() {
    return myShelvedChange != null ? myShelvedChange.getAfterPath() : assertNotNull(myBinaryFile).AFTER_PATH;
  }

  FileStatus getFileStatus() {
    return myShelvedChange != null ? myShelvedChange.getFileStatus() : assertNotNull(myBinaryFile).getFileStatus();
  }

  Change getChange(@NotNull Project project) {
    return myShelvedChange != null ? myShelvedChange.getChange() : assertNotNull(myBinaryFile).createChange(project);
  }

  @Nullable
  public VirtualFile getBeforeVFUnderProject(@NotNull final Project project) {
    if (getBeforePath() == null || project.getBasePath() == null) return null;
    final File baseDir = new File(project.getBasePath());
    final File file = new File(baseDir, getBeforePath());
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
  }

}
