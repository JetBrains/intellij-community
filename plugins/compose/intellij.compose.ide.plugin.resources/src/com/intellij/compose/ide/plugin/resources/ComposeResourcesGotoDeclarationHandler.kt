// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.compose.ide.plugin.resources.ComposeResourcesUsageCollector.ActionType.*
import com.intellij.compose.ide.plugin.resources.ComposeResourcesUsageCollector.ResourceBaseType.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal class ComposeResourcesGotoDeclarationHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
    // find the resource object of the sourceElement
    val kotlinSourceElement = sourceElement?.parent as? KtNameReferenceExpression ?: return null
    val targetResourceItem = getResourceItem(kotlinSourceElement) ?: return null

    // return target psi elements
    return targetResourceItem.getPsiElements().toTypedArray().also {
      val resourceBaseType = if (targetResourceItem.type.isStringType) STRING else FILE
      ComposeResourcesUsageCollector.logAction(NAVIGATE, resourceBaseType, targetResourceItem.type, it.size)
    }
  }
}
