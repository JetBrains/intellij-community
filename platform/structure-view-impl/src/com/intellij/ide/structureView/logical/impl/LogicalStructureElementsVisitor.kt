// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.impl

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.logical.ExternalElementsProvider
import com.intellij.ide.structureView.logical.model.ExtendedLogicalObject
import com.intellij.ide.structureView.logical.model.LogicalPsiDescription
import com.intellij.psi.PsiElement
import com.intellij.ui.tree.TreeVisitor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object LogicalStructureElementsVisitor {

  fun visitPathForLogicalElementSelection(treeElement: StructureViewTreeElement, element: Any?, psiDescriptions: Set<LogicalPsiDescription>): TreeVisitor.Action {
    if (element !is PsiElement) return TreeVisitor.Action.SKIP_CHILDREN
    if (treeElement is ElementsBuilder.LogicalGroupStructureElement<*>) {
      if (treeElement.grouper is ExternalElementsProvider<*, *>) {
        return TreeVisitor.Action.SKIP_CHILDREN
      }
      return TreeVisitor.Action.CONTINUE
    }
    if (treeElement is ElementsBuilder.OtherStructureElement<*>) {
      return TreeVisitor.Action.CONTINUE
    }
    val targetElement = psiDescriptions.firstNotNullOfOrNull {
      it.getSuitableElement(element)
    } ?: return TreeVisitor.Action.SKIP_CHILDREN
    if (treeElement is ElementsBuilder.PsiElementStructureElement<*>) {
      if (treeElement.element == targetElement) {
        return TreeVisitor.Action.INTERRUPT
      }
      (treeElement.getLogicalAssembledModel().model as? ExtendedLogicalObject)?.let {
        if (it.canRepresentPsiElement(targetElement)) return TreeVisitor.Action.INTERRUPT
      }
      if (treeElement.element?.containingFile != targetElement.containingFile) {
        return TreeVisitor.Action.SKIP_CHILDREN
      }
      return TreeVisitor.Action.CONTINUE
    }
    return TreeVisitor.Action.SKIP_CHILDREN
  }

}