// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.symbols

import com.intellij.model.Pointer
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

internal class IntellijModuleSymbol(val moduleId: ModuleId) : NavigatableSymbol, Pointer<IntellijModuleSymbol> {
  override fun createPointer(): Pointer<IntellijModuleSymbol> {
    return this
  }

  override fun dereference(): IntellijModuleSymbol {
    return this
  }

  override fun getNavigationTargets(project: Project): Collection<IntellijModuleNavigationTarget> {
    val moduleEntity = project.workspaceModel.currentSnapshot.resolve(moduleId)
    return if (moduleEntity != null) listOf(IntellijModuleNavigationTarget(moduleEntity, project)) else emptyList()
  }

  override fun equals(other: Any?): Boolean {
    return (other as? IntellijModuleSymbol)?.moduleId == moduleId
  }

  override fun hashCode(): Int {
    return moduleId.hashCode()
  }
}

/**
 * Currently, this class is used directly from [org.jetbrains.idea.devkit.dom.impl.productModules.IntellijModuleConverter]. It would be
 * better to register a reference to [IntellijModuleSymbol] instead.
 */
internal class IntellijModuleNavigationTarget(private val moduleEntity: ModuleEntity,
                                              private val project: Project): NavigationTarget {
  override fun createPointer(): Pointer<IntellijModuleNavigationTarget> = IntellijModuleNavigationTargetPointer(moduleEntity.symbolicId, project)

  override fun computePresentation(): TargetPresentation {
    return TargetPresentation.builder(moduleEntity.name).presentation()
  }

  override fun navigationRequest(): NavigationRequest? {
    findModuleDirectory()?.let { psiDirectory ->
      return NavigationRequest.directoryNavigationRequest(psiDirectory)
    }
    findModuleImlFile()?.let { file -> 
      return NavigationRequest.sourceNavigationRequest(file, TextRange.EMPTY_RANGE)
    }
    return null
  }

  fun findModuleDirectory(): PsiDirectory? {
    val firstContentRoot = moduleEntity.contentRoots.firstNotNullOfOrNull { it.url.virtualFile } ?: return null
    return PsiManager.getInstance(project).findDirectory(firstContentRoot)
  }
  
  fun findModuleImlFile(): PsiFile? {
    val entitySource = moduleEntity.entitySource as? JpsProjectFileEntitySource.FileInDirectory ?: return null
    val imlFile = entitySource.directory.virtualFile?.findChild("${moduleEntity.name}.iml")
    return imlFile?.findPsiFile(project)
  }
} 

private class IntellijModuleNavigationTargetPointer(private val moduleId: ModuleId, private val project: Project) : Pointer<IntellijModuleNavigationTarget> {
  override fun dereference(): IntellijModuleNavigationTarget? {
    if (project.isDisposed) return null
    return project.workspaceModel.currentSnapshot.resolve(moduleId)?.let { 
      IntellijModuleNavigationTarget(it, project) 
    }
  }
}