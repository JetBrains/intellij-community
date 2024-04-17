// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.model.SingleTargetReference
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiCompletableReference
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.File


@Internal
class GradlePluginReference(
  private val myElement: PsiElement,
  private val myRange: TextRange,
  private val myQualifiedName: String
) : SingleTargetReference(), PsiCompletableReference {

  override fun getElement(): PsiElement = myElement

  override fun getRangeInElement(): TextRange = myRange
  override fun resolveSingleTarget(): Symbol? {
    val searchScope = GlobalSearchScope.projectScope(myElement.project)
    val pluginFile = findPrecompiledGroovyPlugin(searchScope)
                     ?: findPrecompiledKotlinPlugin(searchScope)
                     ?: return null
    return GradlePluginSymbol(pluginFile.path, myQualifiedName)
  }

  private fun findPrecompiledGroovyPlugin(searchScope: GlobalSearchScope): VirtualFile? {
    return findPrecompiledPlugin("$myQualifiedName.gradle", searchScope)
  }

  /**
   * Precompiled script plugins on Kotlin could have a package declaration. If plugin ID ([myQualifiedName]) contains dots,
   * probably they split file name and packages (directories, containing a file with plugin).
   */
  private fun findPrecompiledKotlinPlugin(searchScope: GlobalSearchScope): VirtualFile? {
    val leftParts = myQualifiedName.split(".").toMutableList()
    var fileName = ""
    var lastPart = leftParts.removeLastOrNull()
    while (lastPart != null) {
      if (fileName.isEmpty()) {
        fileName = "${lastPart}.gradle.kts"
      } else {
        fileName = "$lastPart.$fileName"
      }
      val file = findPrecompiledPlugin(fileName, searchScope, packageParts = leftParts)
      if (file != null) {
        return file
      }
      lastPart = leftParts.removeLastOrNull()
    }
    return null
  }

  private fun findPrecompiledPlugin(
    fileName: String,
    searchScope: GlobalSearchScope,
    packageParts: List<String> = emptyList()
  ): VirtualFile? {
    val files = FilenameIndex.getVirtualFilesByName(fileName, searchScope)
    for (file in files) {
      if (isPrecompiledPlugin(file, packageParts)) {
        return file
      }
    }
    return null
  }

  /**
   * Checks if the found file matches to conditions of Precompiled script plugins:
   * - if it is a plugin on Groovy, it should have a path like `*\src\main\groovy\*`.
   * - if it is a plugin on Kotlin, it should have a path like `*\src\main\kotlin\*`.
   * - if plugin has a package declaration (e.g., `com.example`), its path should contain packages:
   * (e.g., `*\src\main\kotlin\com.example.my-plugin.gradle.kts*`).
   *
   * There are more strict conditions for Precompiled plugins, not considered here because they require more complicated implementation.
   * @see <a href="https://docs.gradle.org/current/userguide/custom_plugins.html#sec:precompile_script_plugin">Gradle Precompiled plugins</a>
   */
  private fun isPrecompiledPlugin(file: VirtualFile, packageParts: List<String> = emptyList()): Boolean {
    val slash = File.separator
    val language = when (file.extension) {
      "kts" -> "kotlin"
      "gradle" -> "groovy"
      else -> return false
    }
    val packagePath = if (packageParts.isEmpty()) {
      "(?:|.+$slash)" // empty string or any non-zero number of characters ending with a slash
    }
    else {
      packageParts.joinToString(separator = slash, postfix = slash)
    }
    val pathPattern = ".*${slash}src${slash}main${slash}$language${slash}$packagePath${file.presentableName}"
    return file.path.matches(Regex(pathPattern))
  }

  override fun getCompletionVariants(): MutableCollection<LookupElement> {
    TODO("Not yet implemented")
  }
}
