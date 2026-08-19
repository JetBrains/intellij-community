// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity
import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptProcessingFilter

private const val GRADLE_KTS = ".gradle.kts"

/**
 * Processing filter for Gradle Kotlin scripts (`*.gradle.kts`).
 *
 * Returns `true` when the given script should be processed by the Kotlin scripting pipeline.
 * Decision rules:
 * - If the file name does not end with `.gradle.kts`, allow processing (`true`) so non‑Gradle scripts are not blocked.
 * - Allow processing only when Gradle script definitions are available in the project; if none are present,
 *   return `false` to defer processing until the Gradle tooling contributes them.
 *
 * This prevents premature resolution/highlighting of Gradle scripts
 * before their definitions are ready (typically before Gradle import).
 */
class GradleKotlinScriptProcessingFilter(val project: Project) : KotlinScriptProcessingFilter {
    override fun shouldProcessScript(virtualFile: VirtualFile): Boolean =
        !virtualFile.name.endsWith(GRADLE_KTS) ||
                project.workspaceModel.currentSnapshot.entities(GradleScriptDefinitionEntity::class.java).any()
}
