// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ShelvedBinaryFilePatch extends FilePatch {
  private final ShelvedBinaryFile myShelvedBinaryFile;

  @ApiStatus.Internal
  public ShelvedBinaryFilePatch(@NotNull final ShelvedBinaryFile shelvedBinaryFile) {
    myShelvedBinaryFile = shelvedBinaryFile;
    setBeforeName(myShelvedBinaryFile.BEFORE_PATH);
    setAfterName(myShelvedBinaryFile.AFTER_PATH);
  }

  public static ShelvedBinaryFilePatch patchCopy(@NotNull final ShelvedBinaryFilePatch patch) {
    return new ShelvedBinaryFilePatch(patch.getShelvedBinaryFile());
  }

  @Override
  @Nullable
  public String getBeforeFileName() {
    return getFileName(myShelvedBinaryFile.BEFORE_PATH);
  }

  @Override
  @Nullable
  public String getAfterFileName() {
    return getFileName(myShelvedBinaryFile.AFTER_PATH);
  }

  @Nullable
  private static String getFileName(String filePath) {
    return filePath != null ? PathUtil.getFileName(filePath) : null;
  }

  @Override
  public boolean isNewFile() {
    return myShelvedBinaryFile.BEFORE_PATH == null;
  }

  @Override
  public boolean isDeletedFile() {
    return myShelvedBinaryFile.AFTER_PATH == null;
  }

  @ApiStatus.Internal
  @NotNull
  public ShelvedBinaryFile getShelvedBinaryFile() {
    return myShelvedBinaryFile;
  }
}
