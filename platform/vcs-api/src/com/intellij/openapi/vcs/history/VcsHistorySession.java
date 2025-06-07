// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface VcsHistorySession {
  List<VcsFileRevision> getRevisionList();
  VcsRevisionNumber getCurrentRevisionNumber();
  boolean isCurrentRevision(VcsRevisionNumber rev);
  boolean shouldBeRefreshed();
  boolean isContentAvailable(VcsFileRevision revision);
  // i.e. is history for local file (opposite - history for some URL)
  boolean hasLocalSource();

  default @Nullable HistoryAsTreeProvider getHistoryAsTreeProvider() {
    return null;
  }
}
