// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public abstract class VcsFileRevisionEx implements VcsFileRevision {

  public abstract @Nullable @NlsSafe String getAuthorEmail();

  public abstract @Nullable @NlsSafe String getCommitterName();

  public abstract @Nullable @NlsSafe String getCommitterEmail();

  /**
   * Returns the path of the file as it were in this revision
   */
  public abstract @NotNull FilePath getPath();

  public abstract @Nullable Date getAuthorDate();

  public abstract boolean isDeleted();
}
