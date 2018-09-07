// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EmptyHistorySession implements VcsHistorySession {
  @Override
  public List<VcsFileRevision> getRevisionList() {
    return ContainerUtil.emptyList();
  }

  @Override
  public VcsRevisionNumber getCurrentRevisionNumber() {
    return null;
  }

  @Override
  public boolean isCurrentRevision(VcsRevisionNumber rev) {
    return false;
  }

  @Override
  public boolean shouldBeRefreshed() {
    return true;
  }

  @Override
  public boolean isContentAvailable(VcsFileRevision revision) {
    return false;
  }

  @Nullable
  @Override
  public HistoryAsTreeProvider getHistoryAsTreeProvider() {
    return null;
  }

  @Override
  public boolean hasLocalSource() {
    return false;
  }
}
