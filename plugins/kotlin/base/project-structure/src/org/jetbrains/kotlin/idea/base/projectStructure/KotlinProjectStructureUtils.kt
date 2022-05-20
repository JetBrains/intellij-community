// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinProjectStructureUtils")
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.FileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.kotlinSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleProductionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleTestSourceInfo
import org.jetbrains.kotlin.psi.UserDataProperty

val ModuleInfo.kotlinSourceRootType: KotlinSourceRootType?
    get() = when {
        this is ModuleProductionSourceInfo -> SourceKotlinRootType
        this is ModuleTestSourceInfo -> TestSourceKotlinRootType
        else -> null
    }

val Module.productionSourceInfo: ModuleProductionSourceInfo?
    get() {
        val hasProductionRoots = hasRootsOfType(setOf(JavaSourceRootType.SOURCE, SourceKotlinRootType))
                || (isNewMultiPlatformModule && kotlinSourceRootType == SourceKotlinRootType)

        return if (hasProductionRoots) ModuleProductionSourceInfo(this) else null
    }

val Module.testSourceInfo: ModuleTestSourceInfo?
    get() {
        val hasTestRoots = hasRootsOfType(setOf(JavaSourceRootType.TEST_SOURCE, TestSourceKotlinRootType))
                || (isNewMultiPlatformModule && kotlinSourceRootType == TestSourceKotlinRootType)

        return if (hasTestRoots) ModuleTestSourceInfo(this) else null
    }

val Module.sourceModuleInfos: List<ModuleSourceInfo>
    get() = listOfNotNull(testSourceInfo, productionSourceInfo)

private fun Module.hasRootsOfType(rootTypes: Set<JpsModuleSourceRootType<*>>): Boolean {
    return rootManager.contentEntries.any { it.getSourceFolders(rootTypes).isNotEmpty() }
}

var @Suppress("unused") PsiFile.forcedModuleInfo: ModuleInfo? by UserDataProperty(Key.create("FORCED_MODULE_INFO"))
    @ApiStatus.Internal get
    @ApiStatus.Internal set

private val testRootTypes: Set<JpsModuleSourceRootType<*>> = setOf(
    JavaSourceRootType.TEST_SOURCE,
    JavaResourceRootType.TEST_RESOURCE,
    TestSourceKotlinRootType,
    TestResourceKotlinRootType
)

private val sourceRootTypes = setOf<JpsModuleSourceRootType<*>>(
    JavaSourceRootType.SOURCE,
    JavaResourceRootType.RESOURCE,
    SourceKotlinRootType,
    ResourceKotlinRootType
)

val JpsModuleSourceRootType<*>.sourceRootType: KotlinSourceRootType?
    get() = when (this) {
        in sourceRootTypes -> SourceKotlinRootType
        in testRootTypes -> TestSourceKotlinRootType
        else -> null
    }

fun FileIndex.getKotlinSourceRootType(virtualFile: VirtualFile): KotlinSourceRootType? {
    // Ignore injected files
    if (virtualFile is VirtualFileWindow) {
        return null
    }

    return when {
        isUnderSourceRootOfType(virtualFile, testRootTypes) -> TestSourceKotlinRootType
        isInSourceContent(virtualFile) -> SourceKotlinRootType
        else -> null
    }
}