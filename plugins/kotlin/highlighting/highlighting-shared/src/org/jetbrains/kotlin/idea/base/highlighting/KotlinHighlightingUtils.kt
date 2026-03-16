// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinHighlightingUtils")

package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.daemon.SyntheticPsiFileSupport
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
fun KtFile.shouldHighlightErrors(): Boolean {
    if (isCompiled) {
        return false
    }

    if (this is KtCodeFragment && context != null) return true

    val indexingInProgress = isIndexingInProgress(project)
    if (!indexingInProgress && isScript()) return true

    return RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = indexingInProgress).matches(this)
}

@ApiStatus.Internal
fun KtFile.shouldHighlightFile(): Boolean {
    if (isIndexingInProgress(project)) {
        // During indexing (the dumb mode) only compliant (dumb-aware) highlighting passes are executed.
        return RootKindFilter.everything.copy(includeScriptsOutsideSourceRoots = true).matches(this)
    }

    if (isScript()) return true

    return CachedValuesManager.getManager(this.project).getCachedValue(this) {
        Result.create(calculateShouldHighlightFile(), ProjectRootModificationTracker.getInstance(project))
    }
}

private fun isIndexingInProgress(project: Project) = runReadAction { DumbService.getInstance(project).isDumb }

fun KtFile.shouldDefinitelyHighlight(): Boolean =
    (this is KtCodeFragment && context != null) ||
            SyntheticPsiFileSupport.isOutsiderFile(virtualFile) ||
            (this !is KtCodeFragment && virtualFile?.fileSystem is NonPhysicalFileSystem)

@OptIn(KaPlatformInterface::class)
private fun KtFile.calculateShouldHighlightFile(): Boolean =
    shouldDefinitelyHighlight() || RootKindFilter.everything.matches(this) && getKaModule(
        project,
        useSiteModule = null
    ) !is KaNotUnderContentRootModule
