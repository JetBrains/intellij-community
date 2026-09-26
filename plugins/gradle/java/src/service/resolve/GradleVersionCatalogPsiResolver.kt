// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface Coordinates {
  val presentableString: String
}

@ApiStatus.Internal
data class DependencyCoordinates(val group: String, val name: String, val version: String?): Coordinates {
  override val presentableString: String
    get() = if (version != null) "$group:$name:$version" else "$group:$name"

  override fun toString(): String = presentableString

  companion object {
    fun from(coordinates: String): DependencyCoordinates? {
      val parts = coordinates.split(':')
      if (parts.size < 2) return null
      return DependencyCoordinates(parts[0], parts[1], parts.getOrNull(2))
    }
  }
}

@ApiStatus.Internal
data class PluginCoordinates(val id: String, val version: String?): Coordinates {
  override val presentableString: String
    get() = if (version != null) "$id:$version" else id

  override fun toString(): String = presentableString

  companion object {
    fun from(coordinates: String): PluginCoordinates? {
      val parts = coordinates.split(':')
      if (parts.isEmpty()) return null
      return PluginCoordinates(parts[0], parts.getOrNull(1))
    }
  }
}

@ApiStatus.Internal
interface GradleVersionCatalogPsiResolver {
  /**
   * Tries to resolve a dependency from a synthetic accessor method.
   */
  fun getResolvedDependency(method: PsiMethod, context: PsiElement): DependencyCoordinates?

  /**
   * Tries to resolve a plugin from a synthetic accessor method.
   */
  fun getResolvedPlugin(method: PsiMethod, context: PsiElement): PluginCoordinates?

  /**
   * Tries to resolve a dependency from a TOML catalog by accessor path (e.g. "my.lib" for `libs.my.lib`).
   */
  fun getResolvedCoordinatesByPath(catalogName: String, entryPath: String, context: PsiElement): Coordinates?
}

@ApiStatus.Internal
object GradleVersionCatalogPsiResolverUtil : GradleVersionCatalogPsiResolver {
  override fun getResolvedDependency(method: PsiMethod, context: PsiElement, ): DependencyCoordinates? {
    return EP_NAME.extensionList.firstNotNullOfOrNull { it.getResolvedDependency(method, context) }
  }

  override fun getResolvedPlugin(method: PsiMethod, context: PsiElement, ): PluginCoordinates? {
    return EP_NAME.extensionList.firstNotNullOfOrNull { it.getResolvedPlugin(method, context) }
  }

  override fun getResolvedCoordinatesByPath(catalogName: String, entryPath: String, context: PsiElement): Coordinates? {
    return EP_NAME.extensionList.firstNotNullOfOrNull { it.getResolvedCoordinatesByPath(catalogName, entryPath, context) }
  }
}

private val EP_NAME : ExtensionPointName<GradleVersionCatalogPsiResolver> = ExtensionPointName.Companion.create("org.jetbrains.plugins.gradle.versionCatalogPsiResolver")