// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class CompositeBuildData implements Serializable {

  public static final @NotNull Key<CompositeBuildData> KEY = Key.create(CompositeBuildData.class, ProjectKeys.PROJECT.getProcessingWeight() + 1);

  private final String rootProjectPath;
  private final @NotNull List<BuildParticipant> myCompositeParticipants = new ArrayList<>();

  @PropertyMapping({"rootProjectPath"})
  public CompositeBuildData(String rootProjectPath) {
    this.rootProjectPath = rootProjectPath;
  }

  public String getRootProjectPath() {
    return rootProjectPath;
  }

  public @NotNull List<BuildParticipant> getCompositeParticipants() {
    return myCompositeParticipants;
  }
}
