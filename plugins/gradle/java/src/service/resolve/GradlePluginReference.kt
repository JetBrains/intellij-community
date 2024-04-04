// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.model.SingleTargetReference
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiCompletableReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus.Internal

private val pluginFileExtensions = listOf(".gradle", ".gradle.kts")

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
    for (fileExtension in pluginFileExtensions) {
      val files = FilenameIndex.getVirtualFilesByName(myQualifiedName + fileExtension, searchScope)
      val file = files.firstOrNull() ?: continue
      return GradlePluginSymbol(file.path, myQualifiedName)
    }
    return null
  }

  override fun getCompletionVariants(): MutableCollection<LookupElement> {
    TODO("Not yet implemented")
  }
}
