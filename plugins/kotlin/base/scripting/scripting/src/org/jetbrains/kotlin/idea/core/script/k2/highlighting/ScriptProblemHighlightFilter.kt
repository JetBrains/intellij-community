// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.highlighting

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.platform.backend.workspace.findEntitiesByVirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider.Result.create
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.shared.getScriptReports
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.v1.alwaysVirtualFile
import org.jetbrains.kotlin.psi.KtFile
import kotlin.script.experimental.api.ScriptDiagnostic

/**
 * A Kotlin-specific [ProblemHighlightFilter] that enables daemon highlighting for Kotlin scripts
 * only when they are ready.
 *
 * Readiness checks:
 * - Not running on CIDR.
 * - No FATAL script diagnostics.
 * - Script belongs to project sources (scripts outside roots allowed).
 *
 * The decision is cached per file and invalidated on project roots or script
 * dependency changes.
 */
internal class ScriptProblemHighlightFilter : ProblemHighlightFilter() {
    override fun shouldHighlight(psiFile: PsiFile): Boolean {
        if (psiFile !is KtFile || !psiFile.name.endsWith(".kts")) return true
        if (KotlinPlatformUtils.isCidr) return false

        return CachedValuesManager.getManager(psiFile.project).getCachedValue(psiFile) {
            create(
                calculateShouldHighlightScript(psiFile),
                ProjectRootModificationTracker.getInstance(psiFile.project),
                ScriptDependenciesModificationTracker.getInstance(psiFile.project)
            )
        }
    }

    private fun calculateShouldHighlightScript(file: KtFile): Boolean {
        if (getScriptReports(file).any { it.severity == ScriptDiagnostic.Severity.FATAL }) return false

        val workspaceModel = file.project.workspaceModel

        if (workspaceModel.currentSnapshot.getVirtualFileUrlIndex()
                .findEntitiesByVirtualFile(file.alwaysVirtualFile, workspaceModel.getVirtualFileUrlManager())
                .filterIsInstance<KotlinScriptEntity>().none()
        ) return false

        return runReadAction {
            RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true).matches(file)
        }
    }
}
