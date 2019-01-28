// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

/**
 * Marker interface indicating that this is a filter for the VCS log.
 */
public interface VcsLogFilter {
  @NotNull
  VcsLogFilterCollection.FilterKey<?> getKey();

  @NotNull
  String getPresentation();
}
