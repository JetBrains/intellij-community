// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.impl

import com.intellij.ide.TypePresentationService
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.structureView.logical.ContainerElementsProvider
import com.intellij.ide.structureView.logical.ExternalElementsProvider
import com.intellij.ide.structureView.logical.LogicalStructureTreeElementProvider
import com.intellij.ide.structureView.logical.PropertyElementProvider
import com.intellij.ide.structureView.logical.model.LogicalContainerPresentationProvider
import com.intellij.ide.structureView.logical.model.LogicalModelPresentationProvider
import com.intellij.ide.structureView.logical.model.ExtendedLogicalObject
import com.intellij.ide.structureView.logical.model.LogicalStructureAssembledModel
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTarget
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

@ApiStatus.Internal
class LogicalStructureViewModel private constructor(psiFile: PsiFile, editor: Editor?, assembledModel: LogicalStructureAssembledModel<*>, elementBuilder: ElementsBuilder)
  : StructureViewModelBase(psiFile, editor, elementBuilder.createViewTreeElement(assembledModel)),
    StructureViewModel.ElementInfoProvider, StructureViewModel.ExpandInfoProvider, StructureViewModel.ActionHandler {

  constructor(psiFile: PsiFile, editor: Editor?, assembledModel: LogicalStructureAssembledModel<*>):
    this(psiFile, editor, assembledModel, ElementsBuilder())


  override fun isAlwaysShowsPlus(element: StructureViewTreeElement?): Boolean {
    return element is ElementsBuilder.LogicalGroupStructureElement<*>
  }

  override fun isAlwaysLeaf(element: StructureViewTreeElement?): Boolean {
    return element is ElementsBuilder.PropertyStructureElement
  }

  override fun isAutoExpand(element: StructureViewTreeElement): Boolean {
    val model = getModel(element) ?: return false
    return LogicalModelPresentationProvider.getForObject(model)?.isAutoExpand(model) ?: false
  }

  override fun isSmartExpand(): Boolean = false

  override fun handleClick(element: StructureViewTreeElement, fragmentIndex: Int): Boolean {
    val model = getModel(element) ?: return false
    return LogicalModelPresentationProvider.getForObject(model)?.handleClick(model, fragmentIndex) ?: false
  }

  private fun getModel(element: StructureViewTreeElement): Any? {
    return when (element) {
      is ElementsBuilder.LogicalGroupStructureElement<*> -> element.grouper
      is LogicalStructureViewTreeElement<*> -> element.getLogicalAssembledModel().model
      else -> null
    }
  }

}

interface LogicalStructureViewTreeElement<T> : StructureViewTreeElement {

  fun getLogicalAssembledModel(): LogicalStructureAssembledModel<T>

  /**
   * Means that the element is not the node for logical object itself but it's a child which has no own logical object
   */
  @ApiStatus.Internal
  fun isHasNoOwnLogicalModel(): Boolean = false

}

private class ElementsBuilder {

  private val typePresentationService = TypePresentationService.getService()
  private val groupElements: MutableMap<LogicalStructureAssembledModel<*>, MutableMap<ExternalElementsProvider<*, *>, LogicalGroupStructureElement<*>>> = ConcurrentHashMap()

  fun <T> createViewTreeElement(assembledModel: LogicalStructureAssembledModel<T>): StructureViewTreeElement {
    val model = assembledModel.model
    val explicitElement = LogicalStructureTreeElementProvider.getTreeElement(model)
    if (explicitElement != null) return explicitElement
    val psiElement: PsiElement? = getPsiElement(model)
    if (psiElement != null) {
      return PsiElementStructureElement(assembledModel, psiElement)
    }
    return OtherStructureElement(assembledModel)
  }

  private fun getChildrenNodes(assembledModel: LogicalStructureAssembledModel<*>): Collection<StructureViewTreeElement> {
    if (hasSameModelParent(assembledModel)) return emptyList()
    val childrenGrouped = assembledModel.getChildrenGrouped()
    if (childrenGrouped.isEmpty()) {
      return assembledModel.getChildren().map { createViewTreeElement(it) }
    }
    val result = mutableListOf<StructureViewTreeElement>()
    for (pair in childrenGrouped) {
      val groupingObject = pair.first
      val childrenProvider = pair.second
      //if (children.isEmpty()) continue

      if (groupingObject is PropertyElementProvider<*, *>) {
        for (child in childrenProvider()) {
          val psiElement: PsiElement? = getPsiElement(child.model)
          if (psiElement != null) {
            result.add(PropertyPsiElementStructureElement(groupingObject, child, psiElement))
          }
          else {
            result.add(PropertyStructureElement(groupingObject, child))
          }
        }
      }
      else if (groupingObject is ContainerElementsProvider<*, *>
               && LogicalContainerPresentationProvider.getForObject(groupingObject)?.isFlatElements() == true) {
        for (child in childrenProvider()) {
          result.add(createViewTreeElement(child))
        }
      }
      else if (groupingObject is ExternalElementsProvider<*, *>) {
        val groupElement = groupElements.getOrPut(assembledModel) {
          ConcurrentHashMap(mapOf(groupingObject to LogicalGroupStructureElement (assembledModel, groupingObject, childrenProvider)))
        }.getOrPut(groupingObject) {
          LogicalGroupStructureElement(assembledModel, groupingObject, childrenProvider)
        }
        result.add(groupElement)
      } else {
        result.add(LogicalGroupStructureElement(assembledModel, groupingObject, childrenProvider))
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

  private fun getPresentationData(model: Any): PresentationData {
    val presentationProvider = LogicalModelPresentationProvider.getForObject(model)
    if (presentationProvider == null) {
      return PresentationData(
        typePresentationService.getObjectName(model),
        typePresentationService.getTypeName(model),
        typePresentationService.getIcon(model),
        null
      )
    }
    val presentationData = PresentationData()
    val coloredText = presentationProvider.getColoredText(model)
    if (coloredText.isEmpty()) {
      presentationData.presentableText = presentationProvider.getName(model)
      presentationData.locationString = presentationProvider.getTypeName(model)
    }
    for (item in coloredText) {
      presentationData.addText(item)
    }
    presentationData.setIcon(presentationProvider.getIcon(model))
    return presentationData
  }

  private fun getPropertyPresentationData(propertyProvider: PropertyElementProvider<*, *>, model: Any): PresentationData {
    val presentationData = PresentationData()
    val value = if (model is String) model else typePresentationService.getObjectName(model)
    presentationData.addText(propertyProvider.propertyName + ": ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    presentationData.addText("$value ", SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES)
    return presentationData
  }

  private fun hasSameModelParent(assembledModel: LogicalStructureAssembledModel<*>): Boolean {
    var parentTmp = assembledModel.parent
    while (parentTmp != null) {
      val first = parentTmp.model
      val second = assembledModel.model
      if (first is ExtendedLogicalObject && first.logicalEquals(second)
          || second is ExtendedLogicalObject && second.logicalEquals(first)
          || first == second) return true
      parentTmp = parentTmp.parent
    }
    return false
  }

  inner class PsiElementStructureElement<T>(
    private val assembledModel: LogicalStructureAssembledModel<T>,
    psiElement: PsiElement,
  ) : PsiTreeElementBase<PsiElement>(psiElement), LogicalStructureViewTreeElement<T> {

    override fun getPresentableText(): String? = getPresentation().presentableText
    override fun getLocationString(): String? = getPresentation().locationString
    override fun getIcon(open: Boolean): Icon? = getPresentation().getIcon(open)
    override fun getPresentation(): ItemPresentation = getPresentationData(assembledModel.model!!)

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
      return getChildrenNodes(assembledModel)
    }

    override fun isAllowExtensions(): Boolean = false

    override fun getLogicalAssembledModel() = assembledModel

    override fun equals(other: Any?): Boolean {
      if (other !is PsiElementStructureElement<*>) return false
      return assembledModel == other.assembledModel
    }

    override fun hashCode(): Int {
      return assembledModel.hashCode()
    }

  }

  inner class OtherStructureElement<T>(
    private val assembledModel: LogicalStructureAssembledModel<T>,
  ) : StructureViewTreeElement, LogicalStructureViewTreeElement<T> {

    override fun getValue(): Any = assembledModel.model ?: ""

    override fun getLogicalAssembledModel() = assembledModel

    override fun getPresentation(): ItemPresentation = getPresentationData(assembledModel.model!!)

    override fun getChildren(): Array<TreeElement> {
      return getChildrenNodes(assembledModel).toTypedArray()
    }

    override fun equals(other: Any?): Boolean {
      if (other !is OtherStructureElement<*>) return false
      return assembledModel == other.assembledModel
    }

    override fun hashCode(): Int {
      return assembledModel.hashCode()
    }
  }

  inner class LogicalGroupStructureElement<T>(
    val parentAssembledModel: LogicalStructureAssembledModel<T>,
    val grouper: Any,
    private val childrenModelsProvider: () -> List<LogicalStructureAssembledModel<*>>,
  ) : LogicalStructureViewTreeElement<T> {

    private val cashedChildren: Array<TreeElement> by lazy {
      calculateChildren()
    }

    override fun getValue(): Any = grouper

    override fun getPresentation(): ItemPresentation {
      if (grouper is ContainerElementsProvider<*, *>) {
        val presentationProvider = LogicalContainerPresentationProvider.getForObject(grouper)
        if (presentationProvider != null) {
          val coloredText = presentationProvider.getColoredText(parentAssembledModel.model!!)
          val presentationData = PresentationData()
          if (coloredText.isNotEmpty()) {
            coloredText.forEach(presentationData::addText)
            presentationData.setIcon(presentationProvider.getIcon(grouper))
            return presentationData
          }
        }
      }
      return getPresentationData(grouper)
    }

    override fun getChildren(): Array<TreeElement> {
      if (grouper is ExternalElementsProvider<*, *>) {
        return cashedChildren
      }
      return calculateChildren()
    }

    private fun calculateChildren(): Array<TreeElement> {
      return childrenModelsProvider().map { createViewTreeElement(it) }.toTypedArray()
    }

    override fun getLogicalAssembledModel(): LogicalStructureAssembledModel<T> = parentAssembledModel

    override fun isHasNoOwnLogicalModel(): Boolean = true

    override fun equals(other: Any?): Boolean {
      if (other !is LogicalGroupStructureElement<*>) return false
      return parentAssembledModel == other.parentAssembledModel && grouper == other.grouper
    }

    override fun hashCode(): Int {
      return parentAssembledModel.hashCode() * 11 + grouper.hashCode()
    }
  }

  inner class PropertyPsiElementStructureElement<T>(
    private val grouper: PropertyElementProvider<*, *>,
    private val assembledModel: LogicalStructureAssembledModel<T>,
    psiElement: PsiElement,
  ) : PsiTreeElementBase<PsiElement>(psiElement), LogicalStructureViewTreeElement<T> {

    override fun getPresentation(): ItemPresentation = getPropertyPresentationData(grouper, assembledModel.model!!)
    override fun getPresentableText(): String? = getPresentation().presentableText
    override fun getLocationString(): String? = getPresentation().locationString
    override fun getIcon(open: Boolean): Icon? = getPresentation().getIcon(open)

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
      return getChildrenNodes(assembledModel)
    }

    override fun isAllowExtensions(): Boolean = false

    override fun getLogicalAssembledModel() = assembledModel

    override fun equals(other: Any?): Boolean {
      if (other !is PropertyPsiElementStructureElement<*>) return false
      return assembledModel == other.assembledModel
    }

    override fun hashCode(): Int {
      return assembledModel.hashCode()
    }

  }

  inner class PropertyStructureElement(
    private val grouper: PropertyElementProvider<*, *>,
    private val assembledModel: LogicalStructureAssembledModel<*>,
  ) : StructureViewTreeElement {

    override fun getValue(): Any = grouper

    override fun getPresentation(): ItemPresentation = getPropertyPresentationData(grouper, assembledModel.model!!)

    override fun getChildren(): Array<TreeElement> {
      //return getChildrenNodes(assembledModel).toTypedArray()
      return emptyArray()
    }

    override fun equals(other: Any?): Boolean {
      if (other !is PropertyStructureElement) return false
      return assembledModel == other.assembledModel
    }

    override fun hashCode(): Int {
      return assembledModel.hashCode()
    }
  }
}