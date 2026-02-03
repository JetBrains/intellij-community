// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public interface ChangesOnServerTracker extends VcsListener {
  // todo add vcs parameter???
  void invalidate(final Collection<String> paths);

  boolean isUpToDate(@NotNull Change change, @NotNull AbstractVcs vcs);

  boolean updateStep();

  void changeUpdated(@NotNull String path, @NotNull AbstractVcs vcs);

  void changeRemoved(@NotNull String path, @NotNull AbstractVcs vcs);
}
