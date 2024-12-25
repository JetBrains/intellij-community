// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ShelvedBinaryFilePatch extends FilePatch {
  private final ShelvedBinaryFile myShelvedBinaryFile;

  @ApiStatus.Internal
  public ShelvedBinaryFilePatch(final @NotNull ShelvedBinaryFile shelvedBinaryFile) {
    myShelvedBinaryFile = shelvedBinaryFile;
    setBeforeName(myShelvedBinaryFile.BEFORE_PATH);
    setAfterName(myShelvedBinaryFile.AFTER_PATH);
  }

  public static ShelvedBinaryFilePatch patchCopy(final @NotNull ShelvedBinaryFilePatch patch) {
    return new ShelvedBinaryFilePatch(patch.getShelvedBinaryFile());
  }

  @Override
  public @Nullable String getBeforeFileName() {
    return getFileName(myShelvedBinaryFile.BEFORE_PATH);
  }

  @Override
  public @Nullable String getAfterFileName() {
    return getFileName(myShelvedBinaryFile.AFTER_PATH);
  }

  private static @Nullable String getFileName(String filePath) {
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
  public @NotNull ShelvedBinaryFile getShelvedBinaryFile() {
    return myShelvedBinaryFile;
  }
}
