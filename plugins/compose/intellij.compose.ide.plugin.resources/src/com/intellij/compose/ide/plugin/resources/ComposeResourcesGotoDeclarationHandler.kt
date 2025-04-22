// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal class ComposeResourcesGotoDeclarationHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
    val file = sourceElement?.containingFile as? KtFile ?: return null

    val sourceModule = file.module ?: return null

    // find the resource object of the sourceElement
    val kotlinSourceElement = sourceElement.parent as? KtNameReferenceExpression ?: return null
    val targetResourceItem = getResourceItem(kotlinSourceElement) ?: run {
      log.warn("Cannot find Compose resource item for ${kotlinSourceElement.text}")
      return null
    }

    // return target psi elements
    return targetResourceItem.getPsiElements(sourceModule).toTypedArray()
  }
}

private val log by lazy { logger<ComposeResourcesGotoDeclarationHandler>() }