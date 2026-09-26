// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;

public class GradleProjectBuildScriptData implements Serializable {

  public static final @NotNull Key<GradleProjectBuildScriptData> KEY =
    Key.create(GradleProjectBuildScriptData.class, ProjectKeys.MODULE.getProcessingWeight() + 1);

  private final @Nullable File buildScriptSource;

  @PropertyMapping({"buildScriptSource"})
  public GradleProjectBuildScriptData(@Nullable File buildScriptSource) {
    this.buildScriptSource = buildScriptSource;
  }

  public @Nullable File getBuildScriptSource() {
    return buildScriptSource;
  }
}
