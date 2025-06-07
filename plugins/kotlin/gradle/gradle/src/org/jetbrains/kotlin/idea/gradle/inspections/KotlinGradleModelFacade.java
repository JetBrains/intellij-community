// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradle.inspections;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface KotlinGradleModelFacade {
    ExtensionPointName<KotlinGradleModelFacade> EP_NAME = ExtensionPointName.create("org.jetbrains.kotlin.gradleModelFacade");

    default @Nullable String getResolvedVersionByModuleData(
            @NotNull DataNode<?> moduleData,
            @NotNull String groupId,
            @NotNull List<String> libraryIds
    ) {
        return null;
    }

    @NotNull
    Collection<DataNode<ModuleData>> getDependencyModules(@NotNull DataNode<ModuleData> ideModule, @NotNull IdeaProject gradleIdeaProject);
}
