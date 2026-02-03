// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.navigation.NavigatableSymbol
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import icons.GradleIcons
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.util.GradleBundle

@Internal
class GradlePluginSymbol(
  private val filePath: String,
  private val qualifiedName: String
) : NavigatableSymbol,
    SearchTarget {

  init {
    require(filePath.isNotBlank())
  }

  override fun createPointer(): Pointer<out GradlePluginSymbol> = Pointer.hardPointer(this)
  override fun presentation(): TargetPresentation = TargetPresentation
    .builder(GradleBundle.message("gradle.plugin.0", qualifiedName))
    .icon(GradleIcons.Gradle)
    .presentation()

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> {
    val psiFile = findPluginFile(project)
                  ?: return emptyList()
    return listOf(SymbolNavigationService.getInstance().psiFileNavigationTarget(psiFile))
  }

  private fun findPluginFile(project: Project): PsiFile? {
    val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return null
    return PsiManager.getInstance(project).findFile(virtualFile)
  }

  override val usageHandler: UsageHandler get() = UsageHandler.createEmptyUsageHandler(qualifiedName)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GradlePluginSymbol

    return filePath == other.filePath
  }

  override fun hashCode(): Int {
    return filePath.hashCode()
  }
}
