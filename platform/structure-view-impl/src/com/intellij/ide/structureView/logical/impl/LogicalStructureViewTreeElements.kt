// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.impl

import com.intellij.ide.TypePresentationService
import com.intellij.ide.structureView.logical.LogicalStructureTreeElementProvider
import com.intellij.ide.structureView.logical.model.LogicalStructureAssembledModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTarget
import javax.swing.Icon

fun <T> createViewTreeElement(assembledModel: LogicalStructureAssembledModel<T>): StructureViewTreeElement {
  val model = assembledModel.model
  val explicitElement = LogicalStructureTreeElementProvider.getTreeElement(model)
  if (explicitElement != null) return explicitElement
  val psiElement: PsiElement? = when {
    model is PsiElement -> model
    model is PsiTarget && model.isValid() -> model.navigationElement
    else -> null
  }
  if (psiElement != null) {
    return PsiElementStructureElement(assembledModel, psiElement)
  }
  return OtherStructureElement(assembledModel)
}


interface LogicalStructureViewTreeElement<T>: StructureViewTreeElement {

  fun getLogicalAssembledModel(): LogicalStructureAssembledModel<T>

}

private class PsiElementStructureElement<T>(
  private val assembledModel: LogicalStructureAssembledModel<T>,
  psiElement: PsiElement
): PsiTreeElementBase<PsiElement>(psiElement), LogicalStructureViewTreeElement<T> {

  private val typePresentationService = TypePresentationService.getService()

  override fun getPresentableText(): String? = typePresentationService.getObjectName(assembledModel.model!!)

  override fun getLocationString(): String? = typePresentationService.getTypeName(assembledModel.model!!)

  override fun getIcon(open: Boolean): Icon? = typePresentationService.getIcon(assembledModel.model!!)

  override fun getChildrenBase(): Collection<StructureViewTreeElement> {
    return assembledModel.getChildren().map { createViewTreeElement(it) }
  }

  override fun getLogicalAssembledModel() = assembledModel

}

private class OtherStructureElement<T>(
  private val assembledModel: LogicalStructureAssembledModel<T>,
): StructureViewTreeElement, LogicalStructureViewTreeElement<T> {

  private val typePresentationService = TypePresentationService.getService()

  override fun getValue(): Any = assembledModel.model ?: ""

  override fun getLogicalAssembledModel() = assembledModel

  override fun getPresentation(): ItemPresentation {
    return object: ItemPresentation {
      override fun getPresentableText(): String? = typePresentationService.getObjectName(assembledModel.model!!)

      override fun getLocationString(): String? = typePresentationService.getTypeName(assembledModel.model!!)

      override fun getIcon(unused: Boolean): Icon? = typePresentationService.getIcon(assembledModel.model!!)
    }
  }

  override fun getChildren(): Array<TreeElement> {
    return assembledModel.getChildren().map { createViewTreeElement(it) }.toTypedArray()
  }
}