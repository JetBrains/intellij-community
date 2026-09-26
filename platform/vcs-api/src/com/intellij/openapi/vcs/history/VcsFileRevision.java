// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public interface VcsFileRevision extends VcsFileContent, VcsRevisionDescription {
  VcsFileRevision NULL = new VcsFileRevision() {
    @Override
    public @NotNull VcsRevisionNumber getRevisionNumber() {
      return VcsRevisionNumber.NULL;
    }

    @Override
    public Date getRevisionDate() {
      return new Date();
    }

    @Override
    public @NlsSafe String getAuthor() {
      return "";
    }

    @Override
    public @NlsSafe String getCommitMessage() {
      return "";
    }

    @Override
    public @NlsSafe String getBranchName() {
      return null;
    }

    @Override
    public @Nullable RepositoryLocation getChangedRepositoryPath() {
      return null;
    }

    @Override
    public byte[] getContent() {
      return loadContent();
    }

    @Override
    public byte @NotNull [] loadContent() {
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }
  };

  @Nullable
  @NlsSafe
  String getBranchName();

  @Nullable
  RepositoryLocation getChangedRepositoryPath();
}
