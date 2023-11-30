// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.util.PathMapper;
import com.intellij.util.PathMappingSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ProcessControlWithMappings {
  default @NotNull PathMapper getMappingSettings() {
    return new PathMappingSettings(getFileMappings());
  }

  /**
   * The method should be considered obsolete as the only notable usage is only in Python coverage functionality in obsolete pre-targets
   * implementation.
   */
  @ApiStatus.Obsolete
  List<PathMappingSettings.PathMapping> getFileMappings();
}
