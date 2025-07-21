// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.openapi.roots.ProjectRootModificationTracker
import org.jetbrains.kotlin.idea.base.highlighting.KotlinScriptHighlightingExtension
import org.jetbrains.kotlin.idea.base.highlighting.computeIfAbsent
import org.jetbrains.kotlin.idea.base.highlighting.shouldDefinitelyHighlight
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.psi.KtFile
import kotlin.script.experimental.api.ScriptDiagnostic

interface AbstractKotlinScriptHighlightingExtension : KotlinScriptHighlightingExtension {
    fun calculateShouldHighlightScript(file: KtFile): Boolean

    override fun shouldHighlightScript(file: KtFile): Boolean {
        val project = file.project

        return file.computeIfAbsent(
            ProjectRootModificationTracker.getInstance(project),
            ScriptDependenciesModificationTracker.getInstance(project)
        ) {
            calculateShouldHighlightScript(file)
        }
    }

    private fun KtFile.calculateShouldHighlightScript(file: KtFile): Boolean {
        if (shouldDefinitelyHighlight()) return true
        if (KotlinPlatformUtils.isCidr) return false
        if (getScriptReports(file).any { it.severity == ScriptDiagnostic.Severity.FATAL }) return false

        return RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true)
            .matches(this) && this@AbstractKotlinScriptHighlightingExtension.calculateShouldHighlightScript(this)
    }
}