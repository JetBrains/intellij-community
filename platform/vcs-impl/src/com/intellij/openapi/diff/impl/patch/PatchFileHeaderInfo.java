// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class PatchFileHeaderInfo {
  @NotNull private final String myMessage;
  @Nullable private final VcsUser myAuthor;
  @Nullable private final String myBaseRevision;

  PatchFileHeaderInfo(@NotNull String message, @Nullable VcsUser author, @Nullable String revision) {
    myMessage = message;
    myAuthor = author;
    myBaseRevision = revision;
  }

  @NotNull
  public String getMessage() {
    return myMessage;
  }

  @Nullable
  public VcsUser getAuthor() {
    return myAuthor;
  }

  @Nullable
  public String getBaseRevision() {
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