// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiFile
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.config.ALL_KOTLIN_RESOURCE_ROOT_TYPES
import org.jetbrains.kotlin.idea.base.util.KOTLIN_AWARE_SOURCE_ROOT_TYPES

fun isOutsideSourceRootSet(psiFile: PsiFile?, sourceRootTypes: Set<JpsModuleSourceRootType<*>>): Boolean {
    if (psiFile == null || psiFile is PsiCodeFragment) return false
    val file = psiFile.virtualFile ?: return false
    if (file.fileSystem is NonPhysicalFileSystem) return false
    val projectFileIndex = ProjectRootManager.getInstance(psiFile.project).fileIndex
    return !projectFileIndex.isUnderSourceRootOfType(file, sourceRootTypes) && !projectFileIndex.isInLibrary(file)
}

fun isOutsideKotlinAwareSourceRoot(psiFile: PsiFile?) = isOutsideSourceRootSet(psiFile, KOTLIN_AWARE_SOURCE_ROOT_TYPES)

/**
 * @return list of all java source roots in the project which can be suggested as a target directory for a class created by user
 */
fun getSuitableDestinationSourceRoots(project: Project): List<VirtualFile> {
    val roots = ArrayList<VirtualFile>()
    for (module in ModuleManager.getInstance(project).modules) {
        collectSuitableDestinationSourceRoots(module, roots)
    }
    return roots
}

fun getSuitableDestinationSourceRoots(module: Module): MutableList<VirtualFile> {
    val roots = ArrayList<VirtualFile>()
    collectSuitableDestinationSourceRoots(module, roots)
    return roots
}

fun collectSuitableDestinationSourceRoots(module: Module, result: MutableList<VirtualFile>) {
    for (entry in ModuleRootManager.getInstance(module).contentEntries) {
        for (sourceFolder in entry.getSourceFolders(KOTLIN_AWARE_SOURCE_ROOT_TYPES)) {
            if (!isForGeneratedSources(sourceFolder)) {
                ContainerUtil.addIfNotNull(result, sourceFolder.file)
            }
        }
    }
}

fun isForGeneratedSources(sourceFolder: SourceFolder): Boolean {
    val properties = sourceFolder.jpsElement.getProperties(KOTLIN_AWARE_SOURCE_ROOT_TYPES)
    val javaResourceProperties = sourceFolder.jpsElement.getProperties(JavaModuleSourceRootTypes.RESOURCES)
    val kotlinResourceProperties = sourceFolder.jpsElement.getProperties(ALL_KOTLIN_RESOURCE_ROOT_TYPES)
    return properties != null && properties.isForGeneratedSources
            || (javaResourceProperties != null && javaResourceProperties.isForGeneratedSources)
            || (kotlinResourceProperties != null && kotlinResourceProperties.isForGeneratedSources)
}