// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface GraphCommit<Id> {
  @NotNull
  Id getId();

  @NotNull
  List<Id> getParents();

  /**
   * <p>Returns the timestamp indicating the date & time when this commit was made.</p>
   * <p>This time is displayed in the table by default;
   * it is used for joining commits from different repositories;
   * it is used for ordering commits in a single repository (keeping the preference of the topological ordering of course).</p>
   */
  long getTimestamp();
}
