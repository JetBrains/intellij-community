// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public interface VcsDirtyScopeManagerListener {
  Topic<VcsDirtyScopeManagerListener> VCS_DIRTY_SCOPE_UPDATED =
    Topic.create("Vcs Dirty Scope Updated", VcsDirtyScopeManagerListener.class);

  void everythingDirty();
  void filePathsDirty(@NotNull Map<VcsRoot, Set<FilePath>> filesConverted,
                      @NotNull Map<VcsRoot, Set<FilePath>> dirsConverted);
}
