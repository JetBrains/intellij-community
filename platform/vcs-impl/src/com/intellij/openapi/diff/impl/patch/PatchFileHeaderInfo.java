// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Objects;

public final class PatchFileHeaderInfo {
  private final @NotNull @NlsSafe String myMessage;
  private final @Nullable VcsUser myAuthor;
  private final @Nullable String myBaseRevision;

  @VisibleForTesting
  @ApiStatus.Internal
  public PatchFileHeaderInfo(@NotNull @NlsSafe String message, @Nullable VcsUser author, @Nullable String revision) {
    myMessage = message;
    myAuthor = author;
    myBaseRevision = revision;
  }

  public @NotNull @NlsSafe String getMessage() {
    return myMessage;
  }

  public @Nullable VcsUser getAuthor() {
    return myAuthor;
  }

  public @Nullable String getBaseRevision() {
    return myBaseRevision;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PatchFileHeaderInfo info = (PatchFileHeaderInfo)o;
    return Objects.equals(myMessage, info.myMessage) &&
           Objects.equals(myAuthor, info.myAuthor) &&
           Objects.equals(myBaseRevision, info.myBaseRevision);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMessage, myAuthor, myBaseRevision);
  }
}