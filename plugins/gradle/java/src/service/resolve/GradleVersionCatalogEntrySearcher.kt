// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GradleVersionCatalogEntrySearcher {
  /**
   * Returns PsiElement matching to a version catalog entry be the provided [entryPath].
   * The [entryPath] optionally contains a section (`plugins`, `versions`, `bundles` but not `libraries`),
   * and parts of a catalog entry key, separated with dots. For example: `junit.jupiter`, `versions.foo.bar`.
   */
  fun findEntryElement(versionCatalog: PsiFile, entryPath: String): PsiElement?

  fun findEntriesMatching(versionCatalog: PsiFile, entrySearchString: String): List<VersionCatalogEntry>
}

interface VersionCatalogEntry {
  /**
   * Contains a section (`plugins`, `versions`, `bundles`, but not `libraries`), and key parts separated with dots.
   * For example: `junit.jupiter` (if it's a library entry), `plugins.foo.bar`, `versions.foo`, `bundles.bar`
   */
  val pathForBuildScript: String
}


fun findVersionCatalogEntryElement(versionCatalog: PsiFile, entryPath: String): PsiElement? {
  for (extension in EP_NAME.extensionList) {
    val element = extension.findEntryElement(versionCatalog, entryPath) ?: continue
    return element
  }
  return null
}

fun findVersionCatalogEntriesMatching(versionCatalog: PsiFile, entryPath: String): List<VersionCatalogEntry> {
  val result = mutableListOf<VersionCatalogEntry>()
  for (extension in EP_NAME.extensionList) {
    val elements = extension.findEntriesMatching(versionCatalog, entryPath)
    result.addAll(elements)
  }
  return result
}

private val EP_NAME : ExtensionPointName<GradleVersionCatalogEntrySearcher> =
  ExtensionPointName.Companion.create("org.jetbrains.plugins.gradle.versionCatalogEntrySearcher")
