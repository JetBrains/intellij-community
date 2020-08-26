// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public interface VcsRevisionDescription {
  @NotNull
  VcsRevisionNumber getRevisionNumber();

  Date getRevisionDate();

  @Nullable
  @NlsSafe
  String getAuthor();

  @Nullable
  @NlsSafe
  String getCommitMessage();
}
