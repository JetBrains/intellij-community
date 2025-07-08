// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.impl

import com.intellij.ide.TypePresentationService
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.*
import com.intellij.ide.structureView.StructureViewBundle
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.structureView.logical.ContainerElementsProvider
import com.intellij.ide.structureView.logical.ExternalElementsProvider
import com.intellij.ide.structureView.logical.LogicalStructureTreeElementProvider
import com.intellij.ide.structureView.logical.PropertyElementProvider
import com.intellij.ide.structureView.logical.model.*
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTarget
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.TreeVisitor
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

@ApiStatus.Internal
class LogicalStructureViewModel private constructor(psiFile: PsiFile, editor: Editor?, val assembledModel: LogicalStructureAssembledModel<*>, elementBuilder: ElementsBuilder)
  : StructureViewModelBase(psiFile, editor, elementBuilder.createViewTreeElement(assembledModel)),
    StructureViewModel.ElementInfoProvider, StructureViewModel.ExpandInfoProvider, StructureViewModel.ClickHandler {

  constructor(psiFile: PsiFile, editor: Editor?, assembledModel: LogicalStructureAssembledModel<*>):
    this(psiFile, editor, assembledModel, ElementsBuilder())


  override fun isAlwaysShowsPlus(element: StructureViewTreeElement?): Boolean {
    return element is ElementsBuilder.LogicalGroupStructureElement<*>
  }

  override fun isAlwaysLeaf(element: StructureViewTreeElement?): Boolean {
    return element is ElementsBuilder.PropertyStructureElement || element is ElementsBuilder.EmptyChildrenElement<*>
  }

  override fun isAutoExpand(element: StructureViewTreeElement): Boolean {
    val model = getModel(element) ?: return false
    return LogicalModelPresentationProvider.getForObject(model)?.isAutoExpand(model) ?: false
  }

  override fun isSmartExpand(): Boolean = false

  override fun handleClick(event: StructureViewClickEvent): CompletableFuture<Boolean> {
    val model = getModel(event.element)
    val presentation = model?.let { LogicalModelPresentationProvider.getForObject(it) }
    if (model == null || presentation == null) {
      return CompletableFuture.completedFuture(false)
    }
    return presentation.handleClick(model, event.fragmentIndex).thenApply { handled ->
      if (handled) {
        StructureViewEventsCollector.logCustomClickHandled(model::class.java)
      }
      handled
    }
  }

  override fun findAcceptableElement(element: PsiElement?): Any? {
    var elementTmp = element ?: return null
    val psiDescriptions = assembledModel.getLogicalPsiDescriptions()
    while (elementTmp !is PsiFile) {
      for (description in psiDescriptions) {
        val suitableElement = description.getSuitableElement(elementTmp)
        if (suitableElement != null) return suitableElement
      }
      elementTmp = elementTmp.getParent() ?: return null
    }
    return null
  }

  fun visitPathForLogicalElementSelection(treeElement: StructureViewTreeElement, element: Any?, psiDescriptions: Set<LogicalPsiDescription>): TreeVisitor.Action {
    if (element !is PsiElement) return TreeVisitor.Action.SKIP_CHILDREN
    if (treeElement is ElementsBuilder.LogicalGroupStructureElement<*>) {
      if (treeElement.grouper is ExternalElementsProvider<*, *>) {
        return TreeVisitor.Action.SKIP_CHILDREN
      }
      return TreeVisitor.Action.CONTINUE
    }
    val targetElement = psiDescriptions.firstNotNullOfOrNull {
      it.getSuitableElement(element)
    } ?: return TreeVisitor.Action.SKIP_CHILDREN
    if (treeElement is ElementsBuilder.PsiElementStructureElement<*>) {
      if (treeElement.element == targetElement) {
        return TreeVisitor.Action.INTERRUPT
      }
      else if (treeElement.element?.containingFile != targetElement.containingFile) {
        return TreeVisitor.Action.SKIP_CHILDREN
      }
      return TreeVisitor.Action.CONTINUE
    }
    return TreeVisitor.Action.SKIP_CHILDREN
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
    return if (psiElement != null)
      PsiElementStructureElement(assembledModel, psiElement)
    else if (model is ProvidedLogicalContainer<*>)
      LogicalGroupStructureElement(assembledModel, model.provider) { assembledModel.getChildren() }
    else
      OtherStructureElement(assembledModel)
  }

  private fun getChildrenNodes(assembledModel: LogicalStructureAssembledModel<*>): Collection<StructureViewTreeElement> {
    if (assembledModel.hasSameModelParent()) return emptyList()
    val result = mutableListOf<StructureViewTreeElement>()
    for (child in assembledModel.getChildren()) {
      val logicalModel = child.model
      if (logicalModel !is LogicalContainer<*>) {
        result.add(createViewTreeElement(child))
        continue
      }
      if (logicalModel !is ProvidedLogicalContainer<*>) {
        result.add(LogicalGroupStructureElement(assembledModel, logicalModel) { child.getChildren() })
        continue
      }

      val provider = logicalModel.provider
      if (provider is ContainerElementsProvider<*, *>
          && LogicalContainerPresentationProvider.getForObject(provider)?.isFlatElements(logicalModel) == true) {
        for (subChild in child.getChildren()) {
          result.add(createViewTreeElement(subChild))
        }
      }
      else if (provider is PropertyElementProvider<*, *>) {
        for (subChild in child.getChildren()) {
          val psiElement: PsiElement? = getPsiElement(subChild.model)
          if (psiElement != null) {
            result.add(PropertyPsiElementStructureElement(provider, subChild, psiElement))
          }
          else {
            result.add(PropertyStructureElement(provider, subChild))
          }
        }
      }
      else if (provider is ExternalElementsProvider<*, *>) {
        val groupElement = groupElements.getOrPut(assembledModel) {
          ConcurrentHashMap(mapOf(provider to LogicalGroupStructureElement (assembledModel, provider) { child.getChildren() } ))
        }.getOrPut(provider) {
          LogicalGroupStructureElement(assembledModel, provider) { child.getChildren() }
        }
        result.add(groupElement)
      }
      else {
        result.add(LogicalGroupStructureElement(assembledModel, provider) { child.getChildren() })
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
    presentationData.tooltip = presentationProvider.getTooltipText(model)
    return presentationData
  }

  private fun getPropertyPresentationData(propertyProvider: PropertyElementProvider<*, *>, model: Any): PresentationData {
    val presentationData = PresentationData()
    val value = if (model is String) model else typePresentationService.getObjectName(model)
    presentationData.addText(propertyProvider.propertyName + ": ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    presentationData.addText("$value ", SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES)
    return presentationData
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

    override fun navigate(requestFocus: Boolean) {
      StructureViewEventsCollector.logNavigate(assembledModel.model!!::class.java)
      super<PsiTreeElementBase>.navigate(requestFocus)
    }

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
      val result = calculateChildren()
      if (result.isEmpty()) return@lazy arrayOf(EmptyChildrenElement(parentAssembledModel))
      result
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

  class EmptyChildrenElement<T>(
    val parentAssembledModel: LogicalStructureAssembledModel<T>,
  ): LogicalStructureViewTreeElement<T> {
    override fun getPresentation(): ItemPresentation {
      return PresentationData(null, null, null, null).apply {
        this.addText(StructureViewBundle.message("node.structureview.empty"), SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
      }
    }

    override fun getChildren(): Array<out TreeElement?> = emptyArray()
    override fun getValue(): Any? = Any()
    override fun getLogicalAssembledModel() = parentAssembledModel
    override fun isHasNoOwnLogicalModel(): Boolean = true
  }
}