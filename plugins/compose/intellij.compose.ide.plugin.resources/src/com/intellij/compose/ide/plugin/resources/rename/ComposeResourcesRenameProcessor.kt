// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.rename

import com.intellij.compose.ide.plugin.resources.getResourceItem
import com.intellij.compose.ide.plugin.resources.isComposeResourceProperty
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Given a Compose resource declaration, we rename the associated Kotlin property through the [RenameKotlinPropertyProcessor]
 * and the PSI file/xml attribute value we gather inside [prepareRenaming]
 *
 * Using the custom [isToSearchInComments] allows us renaming the string values
 * inside the file which are needed for proper navigation without relying on generation new accessors
 *
 * */
internal class ComposeResourcesRenameProcessor : RenamePsiElementProcessor() {
  override fun canProcessElement(element: PsiElement): Boolean {
    val property = element as? KtProperty ?: return false
    return property.isComposeResourceProperty
  }

  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
    val file = element.containingFile as? KtFile ?: return

    val sourceModule = file.module ?: return

    // find the resource object of the sourceElement
    val kotlinSourceElement = element as? KtProperty ?: return
    val targetResourceItem = getResourceItem(kotlinSourceElement) ?: return

    targetResourceItem.getPsiElements(sourceModule).forEach {
      allRenames[it] = if (it is PsiFile) "$newName.${it.virtualFile.extension}" else newName
    }
  }

  // allows changing the string values present in the generated file when invoked from IntelliJ
  override fun isToSearchInComments(element: PsiElement): Boolean = true
}
