// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.rename

import com.intellij.compose.ide.plugin.resources.ComposeResourcesUsageCollector
import com.intellij.compose.ide.plugin.resources.ComposeResourcesUsageCollector.ActionType.RENAME
import com.intellij.compose.ide.plugin.resources.ComposeResourcesUsageCollector.ResourceBaseType.FILE
import com.intellij.compose.ide.plugin.resources.ComposeResourcesUsageCollector.ResourceBaseType.STRING
import com.intellij.compose.ide.plugin.resources.getResourceItem
import com.intellij.compose.ide.plugin.resources.isComposeResourceProperty
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Given a Compose resource declaration, we rename the associated Kotlin property through the [RenameKotlinPropertyProcessor]
 * and the PSI file/xml attribute value we gather inside [prepareRenaming]
 * */
internal class ComposeResourcesRenameProcessor : RenamePsiElementProcessor() {
  override fun canProcessElement(element: PsiElement): Boolean {
    val property = element as? KtProperty ?: return false
    return property.isComposeResourceProperty
  }

  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
    // find the resource object of the sourceElement
    val kotlinSourceElement = element as? KtProperty ?: return
    val targetResourceItem = getResourceItem(kotlinSourceElement) ?: return

    targetResourceItem.getPsiElements()
      .also {
        val resourceBaseType = if (targetResourceItem.type.isStringType) STRING else FILE
        ComposeResourcesUsageCollector.logAction(RENAME, resourceBaseType, targetResourceItem.type, it.size)
      }
      .forEach {
      allRenames[it] = if (it is PsiFile) "$newName.${it.virtualFile.extension}" else newName
    }
  }
}
