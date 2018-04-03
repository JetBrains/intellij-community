package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

/**
 * Marker interface indicating that this is a filter for the VCS log.
 */
public interface VcsLogFilter {
  @NotNull
  VcsLogFilterCollection.FilterKey<?> getKey();
}
