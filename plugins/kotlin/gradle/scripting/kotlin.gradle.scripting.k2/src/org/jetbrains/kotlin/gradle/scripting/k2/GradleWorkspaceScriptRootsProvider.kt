// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.workspaceModel
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleKotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity
import org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

internal object GradleWorkspaceScriptRootsProvider {
    fun getImportedScriptRoots(project: Project, externalProjectPath: String): Collection<String> {
        return project.workspaceModel.currentSnapshot
            .entitiesBySource {
                it is GradleKotlinScriptEntitySource &&
                        it.projectPath == externalProjectPath &&
                        it.phase == GradleSyncPhase.SCRIPT_MODEL_PHASE
            }
            .filterIsInstance<KotlinScriptEntity>()
            .mapTo(mutableSetOf()) { entity ->
                VfsUtilCore.urlToPath(entity.virtualFileUrl.url).substringBeforeLast("/")
            }
    }

    fun getImportedProjectRoots(project: Project, externalProjectPath: String): Collection<String> {
        return project.workspaceModel.currentSnapshot
            .entities(GradleProjectEntity::class.java)
            .filter { entity ->
                (entity.entitySource as? GradleEntitySource)?.projectPath == externalProjectPath
            }
            .mapTo(mutableSetOf()) { entity ->
                VfsUtilCore.urlToPath(entity.url.url)
            }
    }
}
