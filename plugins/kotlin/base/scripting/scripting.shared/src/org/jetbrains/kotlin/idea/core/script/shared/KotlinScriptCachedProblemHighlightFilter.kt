// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider.Result.create
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
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
 * - Platform-specific predicate [shouldHighlightScript] returns true.
 *
 * The decision is cached per file and invalidated on project roots or script
 * dependency changes.
 */
abstract class KotlinScriptCachedProblemHighlightFilter : ProblemHighlightFilter() {
    abstract fun shouldHighlightScript(file: KtFile): Boolean

    final override fun shouldHighlight(psiFile: PsiFile): Boolean {
        if (psiFile !is KtFile || runReadAction { !psiFile.isScript() }) return true
        if (KotlinPlatformUtils.isCidr) return false

        return CachedValuesManager.getManager(psiFile.project).getCachedValue(psiFile) {
            create(
                psiFile.calculateShouldHighlightScript(),
                ProjectRootModificationTracker.getInstance(psiFile.project),
                ScriptDependenciesModificationTracker.getInstance(psiFile.project)
            )
        }
    }

    private fun KtFile.calculateShouldHighlightScript(): Boolean {
        if (getScriptReports(this).any { it.severity == ScriptDiagnostic.Severity.FATAL }) return false

        return shouldHighlightScript(this) && runReadAction {
            RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true).matches(this)
        }
    }
}
