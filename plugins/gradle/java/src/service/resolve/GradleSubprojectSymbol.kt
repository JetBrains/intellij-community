// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.model.presentation.SymbolPresentation
import icons.GradleIcons
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.util.GradleBundle

@Internal
class GradleSubprojectSymbol(
  private val qualifiedNameParts: List<String>,
  rootProjectPath: String
) : GradleProjectSymbol(rootProjectPath) {

  init {
    require(qualifiedNameParts.isNotEmpty())
  }

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

  override val textSearchStrings: Collection<String> get() = listOf(qualifiedName)

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

    fun qualifiedNameString(qualifiedName: List<String>): String {
      require(qualifiedName.isNotEmpty())
      return qualifiedName.joinToString("") { ":$it" }
    }
  }
}
