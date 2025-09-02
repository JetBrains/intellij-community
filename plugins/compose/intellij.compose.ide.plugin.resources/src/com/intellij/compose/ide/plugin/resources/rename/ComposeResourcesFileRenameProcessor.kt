// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.rename

import com.intellij.compose.ide.plugin.resources.ComposeResourcesUsageCollector
import com.intellij.compose.ide.plugin.resources.ComposeResourcesFileBase
import com.intellij.compose.ide.plugin.resources.withoutExtension
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiFileProcessor
import org.jetbrains.kotlin.idea.refactoring.rename.RenameKotlinPropertyProcessor
import org.jetbrains.kotlin.idea.stubindex.KotlinPropertyShortNameIndex

/**
 * Rename Compose resource files and propagate to code usages
 *
 * Extracts the specific Compose resource property from [KotlinPropertyShortNameIndex]
 * and delegates the rename to [RenameKotlinPropertyProcessor]
 * */
internal class ComposeResourcesFileRenameProcessor : RenamePsiFileProcessor(), ComposeResourcesFileBase {
  override fun canProcessElement(element: PsiElement): Boolean = isComposeResourcesElement(element)

  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
    val property = getKotlinPropertyFromComposeResource(element) ?: return
    val newNameWithoutExtension = newName.withoutExtension
    RenameKotlinPropertyProcessor().prepareRenaming(property, newNameWithoutExtension, allRenames)
    ComposeResourcesUsageCollector.logAction(ComposeResourcesUsageCollector.ActionType.RENAME, fusResourceBaseType, null)
  }

  override val fusActionType: ComposeResourcesUsageCollector.ActionType
    get() = ComposeResourcesUsageCollector.ActionType.RENAME
}
