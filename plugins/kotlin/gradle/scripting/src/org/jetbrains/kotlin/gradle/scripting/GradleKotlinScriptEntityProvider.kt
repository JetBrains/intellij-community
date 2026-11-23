// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.EntitySource
import org.jetbrains.kotlin.gradle.scripting.importing.GradleKotlinDslBaseScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptEntityProvider
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

internal class GradleKotlinScriptEntityProvider : KotlinScriptEntityProvider {
    override fun entitySource(project: Project, virtualFile: VirtualFile): EntitySource? {
        val root = GradleScriptRootResolver.findScriptBuildRoot(project, virtualFile)?.nearest ?: return null
        return GradleKotlinDslBaseScriptEntitySource(root.externalProjectPath, GradleSyncPhase.BASE_SCRIPT_MODEL_PHASE)
    }
}
