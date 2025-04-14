// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinHighlightingUtils")

package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider.Companion.isK1Mode
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.getScriptReports
import org.jetbrains.kotlin.idea.core.script.k2.DefaultScriptResolutionStrategy
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import kotlin.script.experimental.api.ScriptDiagnostic

@ApiStatus.Internal
fun KtFile.shouldHighlightErrors(): Boolean {
    if (isCompiled) {
        return false
    }

    if (this is KtCodeFragment && context != null) {
        return true
    }

    val indexingInProgress = isIndexingInProgress(project)
    if (!indexingInProgress && isScript()) { /* isScript() is based on stub index */
        return calculateShouldHighlightScript()
    }

    return RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = indexingInProgress).matches(this)
}

@ApiStatus.Internal
fun KtFile.shouldHighlightFile(): Boolean {
    if (isIndexingInProgress(project)) {
        // During indexing (the dumb mode) only compliant (dumb-aware) highlighting passes are executed.
        return RootKindFilter.everything.copy(includeScriptsOutsideSourceRoots = true).matches(this)
    }

    return if (isScript()) { /* isScript() is based on stub index */
        computeIfAbsent(ProjectRootModificationTracker.getInstance(project), ScriptDependenciesModificationTracker.getInstance(project)) {
            calculateShouldHighlightScript()
        }
    } else {
        computeIfAbsent(ProjectRootModificationTracker.getInstance(project)) {
            calculateShouldHighlightFile()
        }
    }
}

private fun KtFile.computeIfAbsent(vararg dependencies: Any, compute: KtFile.() -> Boolean): Boolean =
    CachedValuesManager.getManager(project).getCachedValue(this) {
        CachedValueProvider.Result.create(compute(), dependencies)
    }

private fun isIndexingInProgress(project: Project) = runReadAction { DumbService.getInstance(project).isDumb }

private fun KtFile.shouldDefinitelyHighlight(): Boolean =
    (this is KtCodeFragment && context != null) ||
            OutsidersPsiFileSupport.isOutsiderFile(virtualFile) ||
            (this !is KtCodeFragment && virtualFile?.fileSystem is NonPhysicalFileSystem)

@OptIn(KaPlatformInterface::class)
private fun KtFile.calculateShouldHighlightFile(): Boolean =
    shouldDefinitelyHighlight() || RootKindFilter.everything.matches(this) && getKaModule(
        project,
        useSiteModule = null
    ) !is KaNotUnderContentRootModule

private fun KtFile.calculateShouldHighlightScript(): Boolean {
    if (shouldDefinitelyHighlight()) return true

    if (KotlinPlatformUtils.isCidr) return false // There is no Java support in CIDR. So do not highlight errors in KTS if running in CIDR.
    if (getScriptReports(this).any { it.severity == ScriptDiagnostic.Severity.FATAL }) return false

    val isReadyToHighlight = if (isK1Mode()) {
        ScriptConfigurationsProvider.getInstance(project)?.getScriptConfigurationResult(this) != null
    } else {
        val strategy = DefaultScriptResolutionStrategy.getInstance(project)
        strategy.isReadyToHighlight(this).also { isReady -> if (!isReady) strategy.execute(this) }
    }

    return isReadyToHighlight && RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true).matches(this)
}