// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.codeInsight.navigation.PsiElementNavigationTarget
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.model.presentation.PresentableSymbol
import com.intellij.navigation.NavigatableSymbol
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.TargetPopupPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache
import javax.swing.Icon

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
    if (psiFile != null) {
      return listOf(PsiElementNavigationTarget(psiFile))
    }
    return emptyList()
  }

  private fun findBuildFile(project: Project): PsiElement? {
    val rootProject = ExternalProjectDataCache.getInstance(project).getRootExternalProject(rootProjectPath) ?: return null
    val buildFile = externalProject(rootProject)?.buildFile ?: return null
    val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(buildFile) ?: return null
    return PsiManager.getInstance(project).findFile(virtualFile)
  }

  protected abstract fun externalProject(rootProject: ExternalProject): ExternalProject?

  override val usageHandler: UsageHandler<*> get() = UsageHandler.createEmptyUsageHandler(projectName)

  override val presentation: TargetPopupPresentation
    get() {
      val presentation = symbolPresentation
      return object : TargetPopupPresentation {
        override fun getIcon(): Icon? = presentation.icon
        override fun getPresentableText(): String = presentation.longDescription
      }
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
