// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.impl

import com.intellij.ide.TypePresentationService
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.structureView.logical.LogicalStructureTreeElementProvider
import com.intellij.ide.structureView.logical.PropertyElementProvider
import com.intellij.ide.structureView.logical.model.LogicalStructureAssembledModel
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTarget
import java.awt.Font
import javax.swing.Icon

/*
  parentKey is needed to distinguish similar nodes in different branches.
  Because the logical model may be recursive or the same element can be present a few times (e.g: two controllers depend on the same service)
 */
fun <T> createViewTreeElement(assembledModel: LogicalStructureAssembledModel<T>, parentKey: String = "root"): StructureViewTreeElement {
  val model = assembledModel.model
  val explicitElement = LogicalStructureTreeElementProvider.getTreeElement(model)
  if (explicitElement != null) return explicitElement
  val psiElement: PsiElement? = getPsiElement(model)
  if (psiElement != null) {
    return PsiElementStructureElement(assembledModel, psiElement, parentKey)
  }
  return OtherStructureElement(assembledModel, parentKey)
}

private fun getChildrenNodes(assembledModel: LogicalStructureAssembledModel<*>, parentKey: String): Collection<StructureViewTreeElement> {
  val childrenGrouped = assembledModel.getChildrenGrouped()
  if (childrenGrouped.isEmpty()) {
    return assembledModel.getChildren().map { createViewTreeElement(it, parentKey) }
  }
  val result = mutableListOf<StructureViewTreeElement>()
  for (pair in childrenGrouped) {
    val groupingObject = pair.first
    val children = pair.second
    if (children.isEmpty()) continue

    if (groupingObject is PropertyElementProvider<*, *>) {
      for (child in children) {
        val psiElement: PsiElement? = getPsiElement(child.model)
        if (psiElement != null) {
          result.add(PropertyPsiElementStructureElement(groupingObject, child, psiElement, parentKey))
        }
        else {
          result.add(PropertyStructureElement(groupingObject, child, parentKey))
        }
      }
    }
    else {
      result.add(LogicalGroupStructureElement(groupingObject, children, parentKey))
    }

  }
  return result
}

private fun getPsiElement(model: Any?): PsiElement? {
  return when {
    model is PsiElement -> model
    model is PsiTarget && model.isValid() -> model.navigationElement
    else -> null
  }
}

interface LogicalStructureViewTreeElement<T>: StructureViewTreeElement {

  fun getLogicalAssembledModel(): LogicalStructureAssembledModel<T>

}

private class PsiElementStructureElement<T>(
  private val assembledModel: LogicalStructureAssembledModel<T>,
  psiElement: PsiElement,
  private val parentKey: String,
): PsiTreeElementBase<PsiElement>(psiElement), LogicalStructureViewTreeElement<T> {

  private val typePresentationService = TypePresentationService.getService()

  override fun getPresentableText(): String? = typePresentationService.getObjectName(assembledModel.model!!)

  override fun getLocationString(): String? = typePresentationService.getTypeName(assembledModel.model!!)

  override fun getIcon(open: Boolean): Icon? = typePresentationService.getIcon(assembledModel.model!!)

  override fun getChildrenBase(): Collection<StructureViewTreeElement> {
    return getChildrenNodes(assembledModel, parentKey + "." + assembledModel.model.hashCode())
  }

  override fun getLogicalAssembledModel() = assembledModel

  override fun equals(other: Any?): Boolean {
    if (other !is PsiElementStructureElement<*> || parentKey != other.parentKey) return false
    return super.equals(other)
  }

  override fun hashCode(): Int {
    return parentKey.hashCode() * 11 + super.hashCode()
  }

}

private class OtherStructureElement<T>(
  private val assembledModel: LogicalStructureAssembledModel<T>,
  private val parentKey: String,
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
    return getChildrenNodes(assembledModel, parentKey + "." + assembledModel.model.hashCode()).toTypedArray()
  }

  override fun equals(other: Any?): Boolean {
    if (other !is OtherStructureElement<*> || parentKey != other.parentKey) return false
    return super.equals(other)
  }

  override fun hashCode(): Int {
    return parentKey.hashCode() * 11 + super.hashCode()
  }
}

private class LogicalGroupStructureElement(
  private val grouper: Any,
  private val childrenModel: List<LogicalStructureAssembledModel<*>>,
  private val parentKey: String,
): StructureViewTreeElement {

  private val typePresentationService = TypePresentationService.getService()

  override fun getValue(): Any = grouper

  override fun getPresentation(): ItemPresentation {
    return object: ItemPresentation {
      override fun getPresentableText(): String? = typePresentationService.getObjectName(grouper)

      override fun getLocationString(): String? = typePresentationService.getTypeName(grouper)

      override fun getIcon(unused: Boolean): Icon? = typePresentationService.getIcon(grouper)
    }
  }

  override fun getChildren(): Array<TreeElement> {
    return childrenModel.map { createViewTreeElement(it, parentKey) }.toTypedArray()
  }

  override fun equals(other: Any?): Boolean {
    if (other !is LogicalGroupStructureElement || parentKey != other.parentKey) return false
    return super.equals(other)
  }

  override fun hashCode(): Int {
    return parentKey.hashCode() * 11 + super.hashCode()
  }
}

private class PropertyPsiElementStructureElement<T>(
  private val grouper: PropertyElementProvider<*, *>,
  private val assembledModel: LogicalStructureAssembledModel<T>,
  psiElement: PsiElement,
  private val parentKey: String,
): PsiTreeElementBase<PsiElement>(psiElement), LogicalStructureViewTreeElement<T> {

  private val typePresentationService = TypePresentationService.getService()

  override fun getPresentableText(): String = grouper.propertyName + ": " + typePresentationService.getObjectName(assembledModel.model!!)

  override fun getLocationString(): String? = null //typePresentationService.getObjectName(assembledModel.model!!)

  override fun getIcon(open: Boolean): Icon? = null //typePresentationService.getIcon(assembledModel.model!!)

  override fun getChildrenBase(): Collection<StructureViewTreeElement> {
    return emptyList() // getChildrenNodes (assembledModel, parentKey + "." + assembledModel.model.hashCode())
  }

  override fun getLogicalAssembledModel() = assembledModel

  override fun equals(other: Any?): Boolean {
    if (other !is PropertyPsiElementStructureElement<*> || parentKey != other.parentKey) return false
    return super.equals(other)
  }

  override fun hashCode(): Int {
    return parentKey.hashCode() * 11 + super.hashCode()
  }

}

private class PropertyStructureElement(
  private val grouper: PropertyElementProvider<*, *>,
  private val assembledModel: LogicalStructureAssembledModel<*>,
  private val parentKey: String,
): StructureViewTreeElement {

  private val typePresentationService = TypePresentationService.getService()

  override fun getValue(): Any = grouper

  override fun getPresentation(): ItemPresentation {
    return object: ItemPresentation {
      override fun getPresentableText(): String? = grouper.propertyName

      override fun getLocationString(): String? = typePresentationService.getObjectName(assembledModel.model!!)

      override fun getIcon(unused: Boolean): Icon? = null //typePresentationService.getIcon(assembledModel.model!!)
    }
  }

  override fun getChildren(): Array<TreeElement> {
    return getChildrenNodes(assembledModel, parentKey + "." + assembledModel.model.hashCode()).toTypedArray()
  }

  override fun equals(other: Any?): Boolean {
    if (other !is PropertyStructureElement || parentKey != other.parentKey) return false
    return super.equals(other)
  }

  override fun hashCode(): Int {
    return parentKey.hashCode() * 11 + super.hashCode()
  }
}