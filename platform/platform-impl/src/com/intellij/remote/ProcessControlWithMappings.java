// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.util.PathMapper;
import com.intellij.util.PathMappingSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ProcessControlWithMappings {
  @NotNull
 default PathMapper getMappingSettings() {
    return new PathMappingSettings(getFileMappings());
  }

  List<PathMappingSettings.PathMapping> getFileMappings();
}
