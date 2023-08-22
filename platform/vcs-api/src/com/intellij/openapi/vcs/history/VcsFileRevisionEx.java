// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public abstract class VcsFileRevisionEx implements VcsFileRevision {

  @Nullable
  public abstract @NlsSafe String getAuthorEmail();

  @Nullable
  public abstract @NlsSafe String getCommitterName();

  @Nullable
  public abstract @NlsSafe String getCommitterEmail();

  /**
   * Returns the path of the file as it were in this revision
   */
  @NotNull
  public abstract FilePath getPath();

  @Nullable
  public abstract Date getAuthorDate();

  public abstract boolean isDeleted();
}
