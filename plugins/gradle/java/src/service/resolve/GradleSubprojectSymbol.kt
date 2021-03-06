// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.model.Pointer
import com.intellij.model.presentation.SymbolPresentation
import com.intellij.model.search.SearchRequest
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.api.ReplaceTextTarget
import com.intellij.refactoring.rename.api.ReplaceTextTargetContext
import com.intellij.util.containers.init
import icons.GradleIcons
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.util.GradleBundle

@Internal
class GradleSubprojectSymbol(
  private val qualifiedNameParts: List<String>,
  rootProjectPath: String
) : GradleProjectSymbol(rootProjectPath), RenameTarget {

  init {
    require(qualifiedNameParts.isNotEmpty())
  }

  override fun createPointer(): Pointer<out GradleSubprojectSymbol> = Pointer.hardPointer(this)

  override val projectName: String get() = qualifiedNameParts.last()
  override val qualifiedName: String get() = qualifiedNameString(qualifiedNameParts)

  private val myPresentation = SymbolPresentation.create(
    GradleIcons.Gradle,
    projectName,
    GradleBundle.message("gradle.project.0", projectName),
    GradleBundle.message("gradle.project.0", qualifiedName)
  )

  override fun getSymbolPresentation(): SymbolPresentation = myPresentation

  override fun externalProject(rootProject: ExternalProject): ExternalProject? {
    return qualifiedNameParts.fold<String, ExternalProject?>(rootProject) { extProject, name ->
      extProject?.childProjects?.get(name)
    }
  }

  override val textSearchRequests: Collection<SearchRequest> get() = listOf(SearchRequest.of(qualifiedName))

  override val targetName: String get() = projectName

  override val maximalSearchScope: SearchScope? get() = null

  override fun textTargets(context: ReplaceTextTargetContext): Collection<ReplaceTextTarget> {
    val parentQualifiedName = qualifiedNameParts.init()
    val prefix = if (parentQualifiedName.isEmpty()) {
      separator
    }
    else {
      qualifiedNameString(parentQualifiedName) + separator
    }
    return listOf(ReplaceTextTarget(
      textSearchRequest = SearchRequest.of(qualifiedName),
      usageTextByName = { newName -> prefix + newName }
    ))
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as GradleSubprojectSymbol

    if (qualifiedNameParts != other.qualifiedNameParts) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + qualifiedNameParts.hashCode()
    return result
  }

  companion object {

    const val separator = ":"

    fun qualifiedNameString(qualifiedName: List<String>): String {
      require(qualifiedName.isNotEmpty())
      return qualifiedName.joinToString("") { "$separator$it" }
    }
  }
}
