// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.importing.ExternalProjectStructureCustomizer;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.Identifiable;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
public final class GradleProjectStructureCustomizer extends ExternalProjectStructureCustomizer {

  @Override
  public @NotNull Set<? extends Key<?>> getIgnorableDataKeys() {
    return getDataKeys();
  }

  @Override
  public @NotNull Set<? extends Key<?>> getPublicDataKeys() {
    return getDataKeys();
  }

  @Override
  public @NotNull Set<? extends Key<? extends Identifiable>> getDependencyAwareDataKeys() {
    return getDataKeys();
  }

  @Override
  public @Nullable Icon suggestIcon(@NotNull DataNode node, @NotNull ExternalSystemUiAware uiAware) {
    return null;
  }

  @Override
  public @NotNull Couple<@Nls String> getRepresentationName(@NotNull DataNode node) {
    if (node.getKey().equals(GradleSourceSetData.KEY)) {
      final GradleSourceSetData data = (GradleSourceSetData)node.getData();
      String comment = StringUtil.substringAfter(data.getExternalName(), ":");
      return Couple.of(GradleBundle.message("gradle.project.structure.source.set"), comment);
    }
    if (node.getKey().equals(ProjectKeys.MODULE)) {
      ModuleData moduleData = (ModuleData)node.getData();
      return Couple.of(moduleData.getExternalName(), null);
    }
    return super.getRepresentationName(node);
  }

  private static @NotNull Set<? extends Key<? extends Identifiable>> getDataKeys() {
    return Set.of(GradleSourceSetData.KEY, ProjectKeys.MODULE);
  }
}
