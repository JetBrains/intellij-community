// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;


public class CurrentBinaryContentRevision extends CurrentContentRevision implements BinaryContentRevision {
  public CurrentBinaryContentRevision(final FilePath file) {
    super(file);
  }

  @Override
  public byte @Nullable [] getBinaryContent() throws VcsException {
    return getContentAsBytes();
  }

  @Override
  public @NonNls String toString() {
    return "CurrentBinaryContentRevision:" + myFile;
  }
}