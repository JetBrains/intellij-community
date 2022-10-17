// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.model.presentation.PresentableSymbol
import com.intellij.navigation.NavigatableSymbol
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.SymbolNavigationService
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache

@Internal
abstract class GradleProjectSymbol(
  protected val rootProjectPath: String
) : PresentableSymbol,
    NavigatableSymbol,
    SearchTarget {

  init {
    require(rootProjectPath.isNotBlank())
  }

  override fun createPointer(): Pointer<out GradleProjectSymbol> = Pointer.hardPointer(this)

  abstract val projectName: String
  abstract val qualifiedName: String

  final override fun getNavigationTargets(project: Project): Collection<NavigationTarget> {
    val psiFile = findBuildFile(project)
                  ?: return emptyList()
    return listOf(SymbolNavigationService.getInstance().psiFileNavigationTarget(psiFile))
  }

  private fun findBuildFile(project: Project): PsiFile? {
    val rootProject = ExternalProjectDataCache.getInstance(project).getRootExternalProject(rootProjectPath) ?: return null
    val buildFile = externalProject(rootProject)?.buildFile ?: return null
    val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(buildFile) ?: return null
    return PsiManager.getInstance(project).findFile(virtualFile)
  }

  protected abstract fun externalProject(rootProject: ExternalProject): ExternalProject?

  override val usageHandler: UsageHandler get() = UsageHandler.createEmptyUsageHandler(projectName)

  override fun presentation(): TargetPresentation {
    val presentation = symbolPresentation
    return TargetPresentation
      .builder(presentation.longDescription)
      .icon(presentation.icon)
      .presentation()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GradleProjectSymbol

    if (rootProjectPath != other.rootProjectPath) return false

    return true
  }

  override fun hashCode(): Int {
    return rootProjectPath.hashCode()
  }
}
