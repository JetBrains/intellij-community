// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.importing

import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.KotlinGradleScriptEntitySource
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

internal class KotlinDslScriptSyncExtension : GradleSyncExtension {

    override fun updateProjectModel(
      context: ProjectResolverContext, syncStorage: MutableEntityStorage, projectStorage: MutableEntityStorage, phase: GradleSyncPhase
    ) {
        if (phase == GradleSyncPhase.BASE_SCRIPT_MODEL_PHASE || phase == GradleSyncPhase.ADDITIONAL_MODEL_PHASE) {
            projectStorage.replaceBySource({ it is KotlinGradleScriptEntitySource }, syncStorage)
        }
    }
}