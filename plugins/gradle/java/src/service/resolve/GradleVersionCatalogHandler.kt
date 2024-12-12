// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to handle gradle version catalogs
 */
@ApiStatus.Internal
interface GradleVersionCatalogHandler {
  @Deprecated("Doesn't work for included builds of a composite build", ReplaceWith("getVersionCatalogFiles(module)"))
  fun getExternallyHandledExtension(project: Project) : Set<String>

  @Deprecated("Doesn't work for included builds of a composite build", ReplaceWith("getVersionCatalogFiles(module)"))
  fun getVersionCatalogFiles(project: Project) : Map</*catalog name*/ String, /*catalog file*/ VirtualFile>
  fun getVersionCatalogFiles(module: Module) : Map</*catalog name*/ String, /*catalog file*/ VirtualFile>

  fun getAccessorClass(context: PsiElement, catalogName: String) : PsiClass?
  fun getAccessorsForAllCatalogs(context: PsiElement) : Map</*catalog name*/ String, /*accessor*/ PsiClass>
}

@Deprecated("Doesn't work for included builds of a composite build", ReplaceWith("getVersionCatalogFiles(module)"))
fun getVersionCatalogFiles(project: Project) : Map<String, VirtualFile> {
  val container = mutableMapOf<String, VirtualFile>()
  for (extension in EP_NAME.extensionList) {
    container.putAll(extension.getVersionCatalogFiles(project))
  }
  return container
}

/**
 * Provides version catalogs for a Gradle build corresponding to the given module.
 * The build could be not only the main (in a root project directory), but also an included build (linked project) of a composite build.
 * @return a map between a version catalog name and a file with this catalog.
 */
fun getVersionCatalogFiles(module: Module) : Map<String, VirtualFile> {
  val container = mutableMapOf<String, VirtualFile>()
  for (extension in EP_NAME.extensionList) {
    container.putAll(extension.getVersionCatalogFiles(module))
  }
  return container
}

@Deprecated("Doesn't work for included builds of a composite build", ReplaceWith("getVersionCatalogFiles(module)"))
fun getGradleStaticallyHandledExtensions(project: Project) : Set<String> {
  val container = mutableSetOf<String>()
  for (extension in EP_NAME.extensionList) {
    container.addAll(extension.getExternallyHandledExtension(project))
  }
  return container
}

fun getVersionCatalogAccessor(context: PsiElement, name: String) : PsiClass? {
  for (extension in EP_NAME.extensionList) {
    return extension.getAccessorClass(context, name) ?: continue
  }
  return null
}

/**
 * Provides accessors for all version catalogs of a build the context element belongs to (maps catalog name to accessor)
 */
fun getAccessorsForAllCatalogs(context: PsiElement) : Map<String, PsiClass> {
  val container = mutableMapOf<String, PsiClass>()
  for (extension in EP_NAME.extensionList) {
    container.putAll(extension.getAccessorsForAllCatalogs(context))
  }
  return container
}

private val EP_NAME : ExtensionPointName<GradleVersionCatalogHandler> = ExtensionPointName.create("org.jetbrains.plugins.gradle.externallyHandledExtensions")