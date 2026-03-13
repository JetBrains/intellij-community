// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinProjectStructureUtils")

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analyzer.LanguageSettingsProvider
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.KotlinSourceRootType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.ResourceKotlinRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestResourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.kotlinSourceRootType
import org.jetbrains.kotlin.idea.base.util.runWithAlternativeResolveEnabled
import org.jetbrains.kotlin.psi.UserDataProperty

private fun Module.hasRootsOfType(rootTypes: Set<JpsModuleSourceRootType<*>>): Boolean {
    return rootManager.contentEntries.any { it.getSourceFolders(rootTypes).isNotEmpty() }
}

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

fun ProjectFileIndex.isInTestSource(virtualFile: VirtualFile): Boolean {
    if (virtualFile is VirtualFileWindow) {
        return false
    }
    val sourceRootType = runReadAction {
        getContainingSourceRootType(virtualFile)
    }
    return sourceRootType in testRootTypes
}

fun ProjectFileIndex.getKotlinSourceRootType(virtualFile: VirtualFile): KotlinSourceRootType? {
    // Ignore injected files
    if (virtualFile is VirtualFileWindow) {
        return null
    }

    return runReadAction {
        val sourceRootType = getContainingSourceRootType(virtualFile) ?: return@runReadAction null
        when (sourceRootType) {
            in testRootTypes -> TestSourceKotlinRootType
            in sourceRootTypes -> SourceKotlinRootType
            else -> null
        }
    }
}

fun Module.getKotlinSourceRootType(): KotlinSourceRootType? =
    when {
        hasRootsOfType(sourceRootTypes) -> SourceKotlinRootType
        hasRootsOfType(testRootTypes) -> TestSourceKotlinRootType
        else -> null
    }

@RequiresReadLock
fun GlobalSearchScope.hasKotlinJvmRuntime(project: Project): Boolean {
    return project.runWithAlternativeResolveEnabled {
        try {
            val markerClassName = StandardNames.FqNames.unit.asString()
            JavaPsiFacade.getInstance(project).findClass(markerClassName, this@hasKotlinJvmRuntime) != null
        } catch (e: IndexNotReadyException) {
            false
        }
    }
}

@ApiStatus.Internal
fun JpsModuleSourceRoot.getMigratedSourceRootTypeWithProperties(): Pair<JpsModuleSourceRootType<JpsElement>, JpsElement>? {
    val currentRootType = rootType

    @Suppress("UNCHECKED_CAST")
    val newSourceRootType: JpsModuleSourceRootType<JpsElement> = when (currentRootType) {
        JavaSourceRootType.SOURCE -> SourceKotlinRootType as JpsModuleSourceRootType<JpsElement>
        JavaSourceRootType.TEST_SOURCE -> TestSourceKotlinRootType
        JavaResourceRootType.RESOURCE -> ResourceKotlinRootType
        JavaResourceRootType.TEST_RESOURCE -> TestResourceKotlinRootType
        else -> return null
    } as JpsModuleSourceRootType<JpsElement>

    val properties = getProperties(rootType)?.also { (it as? JpsElementBase<*>)?.setParent(null) }
        ?: rootType.createDefaultProperties()

    return newSourceRootType to properties
}

val Module.hasProductionSource: Boolean
    get() {
        return hasRootsOfType(
            setOf(
                JavaSourceRootType.SOURCE,
                SourceKotlinRootType
            )
        ) || (isNewMultiPlatformModule && kotlinSourceRootType == SourceKotlinRootType)

    }


val Module.hasTestSource: Boolean
    get() {
        return hasRootsOfType(
            setOf(
                JavaSourceRootType.TEST_SOURCE,
                TestSourceKotlinRootType
            )
        ) || (isNewMultiPlatformModule && kotlinSourceRootType == TestSourceKotlinRootType)

    }