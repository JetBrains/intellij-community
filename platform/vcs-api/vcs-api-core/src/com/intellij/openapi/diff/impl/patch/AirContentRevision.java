// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public interface AirContentRevision {
  boolean isBinary();

  @NotNull
  String getContentAsString() throws VcsException;

  byte @Nullable [] getContentAsBytes() throws VcsException;

  @Nullable
  String getRevisionNumber();

  @Nullable
  Long getLastModifiedTimestamp();

  @NotNull
  FilePath getPath();

  default @Nullable Charset getCharset() {
    return null;
  }

  default @Nullable String getLineSeparator() {
    return null;
  }
}
