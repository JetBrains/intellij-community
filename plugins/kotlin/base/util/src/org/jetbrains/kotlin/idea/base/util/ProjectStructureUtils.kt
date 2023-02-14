// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectStructureUtils")
package org.jetbrains.kotlin.idea.base.util

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport
import com.intellij.facet.FacetManager
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FileTypeIndex
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.config.ALL_KOTLIN_SOURCE_ROOT_TYPES
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.nio.file.Paths

val KOTLIN_FILE_EXTENSIONS: Set<String> = setOf("kt", "kts")
val KOTLIN_FILE_TYPES: Set<KotlinFileType> = setOf(KotlinFileType.INSTANCE)

fun Module.isAndroidModule(modelsProvider: IdeModifiableModelsProvider? = null): Boolean {
    val facetModel = modelsProvider?.getModifiableFacetModel(this) ?: FacetManager.getInstance(this)
    val facets = facetModel.allFacets
    return facets.any { it.javaClass.simpleName == "AndroidFacet" }
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

fun hasKotlinFilesInSources(module: Module): Boolean {
    return FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, module.getModuleScope(false))
}

fun hasKotlinFilesInTestsOnly(module: Module): Boolean {
    return !hasKotlinFilesInSources(module)
            && FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, module.getModuleScope(true))
}

fun OrderEnumerator.findLibrary(predicate: (Library) -> Boolean): Library? {
    var result: Library? = null

    forEachLibrary { library ->
        if (predicate(library)) {
            result = library
            return@forEachLibrary false
        }

        return@forEachLibrary true
    }

    return result
}

fun Module.findLibrary(predicate: (Library) -> Boolean): Library? {
    return OrderEnumerator.orderEntries(this).findLibrary(predicate)
}

fun Project.containsKotlinFile(): Boolean = FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, projectScope())

fun Project.containsNonScriptKotlinFile(): Boolean = !FileTypeIndex.processFiles(
    KotlinFileType.INSTANCE,
    { it.toPsiFile(this)?.safeAs<KtFile>()?.isScript() != false },
    projectScope(),
)

val Module.sdk: Sdk?
    get() = ModuleRootManager.getInstance(this).sdk

fun Library.update(block: (Library.ModifiableModel) -> Unit) {
    val modifiableModel = this.modifiableModel
    try {
        block(modifiableModel)
    } finally {
        modifiableModel.commit()
    }
}

fun LibraryEx.updateEx(block: (LibraryEx.ModifiableModelEx) -> Unit) {
    val modifiableModel = this.modifiableModel
    try {
        block(modifiableModel)
    } finally {
        modifiableModel.commit()
    }
}

private val KOTLIN_AWARE_SOURCE_ROOT_TYPES: Set<JpsModuleSourceRootType<JavaSourceRootProperties>> =
    JavaModuleSourceRootTypes.SOURCES + ALL_KOTLIN_SOURCE_ROOT_TYPES

fun PsiElement.isUnderKotlinSourceRootTypes(): Boolean {
    val ktFile = this.containingFile.safeAs<KtFile>() ?: return false
    val file = ktFile.virtualFile?.takeIf { it !is VirtualFileWindow && it.fileSystem !is NonPhysicalFileSystem } ?: return false
    val projectFileIndex = ProjectRootManager.getInstance(ktFile.project).fileIndex
    return projectFileIndex.isUnderSourceRootOfType(file, KOTLIN_AWARE_SOURCE_ROOT_TYPES)
}

private val GRADLE_SYSTEM_ID = ProjectSystemId("GRADLE")

val Module.isGradleModule: Boolean
    get() = ExternalSystemApiUtil.isExternalSystemAwareModule(GRADLE_SYSTEM_ID, this)

private val MAVEN_SYSTEM_ID = ProjectSystemId("Maven")

val Module.isMavenModule: Boolean
    get() = ExternalSystemApiUtil.isExternalSystemAwareModule(MAVEN_SYSTEM_ID, this)