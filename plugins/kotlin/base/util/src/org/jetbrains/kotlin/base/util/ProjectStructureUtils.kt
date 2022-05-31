// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectStructureUtils")
package org.jetbrains.kotlin.base.util

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport
import com.intellij.facet.FacetManager
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinFileType

val KOTLIN_FILE_EXTENSIONS: Set<String> = setOf("kt", "kts")
val KOTLIN_FILE_TYPES: Set<KotlinFileType> = setOf(KotlinFileType.INSTANCE)

fun Module.isAndroidModule(modelsProvider: IdeModifiableModelsProvider? = null): Boolean {
    val facetModel = modelsProvider?.getModifiableFacetModel(this) ?: FacetManager.getInstance(this)
    val facets = facetModel.allFacets
    return facets.any { it.javaClass.simpleName == "AndroidFacet" }
}

/**
 * This method is equivalent to {@sample invalidateProjectRoots(RootsChangeRescanningInfo.TOTAL_RESCAN)}
 * Consider using optimised instance of  [com.intellij.util.indexing.BuildableRootsChangeRescanningInfo]
 */
@ApiStatus.Internal
fun Project.invalidateProjectRoots() {
    ProjectRootManagerEx.getInstanceEx(this).makeRootsChange(EmptyRunnable.INSTANCE, false, true)
}

@ApiStatus.Internal
fun Project.invalidateProjectRoots(info: RootsChangeRescanningInfo) {
    ProjectRootManagerEx.getInstanceEx(this).makeRootsChange(EmptyRunnable.INSTANCE, info)
}

val VirtualFile.parentsWithSelf: Sequence<VirtualFile>
    get() = generateSequence(this) { it.parent }

fun getOutsiderFileOrigin(project: Project, file: VirtualFile): VirtualFile? {
    if (!OutsidersPsiFileSupport.isOutsiderFile(file)) {
        return null
    }

    val originalFilePath = OutsidersPsiFileSupport.getOriginalFilePath(file) ?: return null
    val originalFile = VfsUtil.findFile(Paths.get(originalFilePath), false) ?: return null

    // TODO possibly change to 'GlobalSearchScope.projectScope(project)' check
    val projectDir = project.baseDir

    return originalFile.parentsWithSelf
        .takeWhile { it != projectDir }
        .firstOrNull { it.exists() }
}