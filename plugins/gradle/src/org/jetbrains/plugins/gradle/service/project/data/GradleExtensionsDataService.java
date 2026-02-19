// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.data;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleExtensions;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings;

import java.util.Collection;

/**
 * @author Vladislav.Soroka
 */
@Order(ExternalSystemConstants.UNORDERED)
public final class GradleExtensionsDataService extends AbstractProjectDataService<GradleExtensions, Module> {

  public static final @NotNull Key<GradleExtensions> KEY =
    Key.create(GradleExtensions.class, BuildScriptClasspathData.KEY.getProcessingWeight() + 1);

  @Override
  public @NotNull Key<GradleExtensions> getTargetDataKey() {
    return KEY;
  }

  @Override
  public void importData(@NotNull Collection<? extends DataNode<GradleExtensions>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (projectData == null || toImport.isEmpty()) {
      return;
    }

    GradleExtensionsSettings.getInstance(project).add(projectData.getLinkedExternalProjectPath(), toImport);
  }
}
