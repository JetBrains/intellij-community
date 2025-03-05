// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.*
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.progress.blockingContext
import com.intellij.ui.ColorUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.TreeDomainModel
import com.intellij.ui.treeStructure.TreeNodeDomainModel
import com.intellij.ui.treeStructure.TreeNodePresentation
import com.intellij.ui.treeStructure.TreeNodePresentationBuilder
import com.intellij.util.SmartList
import com.intellij.util.ui.tree.LegacyCompatibilityTreeNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Internal
@ApiStatus.Experimental
@ApiStatus.Obsolete
class TreeStructureDomainModelAdapter(
  private val structure: AbstractTreeStructure,
  private val useReadAction: Boolean,
  concurrency: Int,
) : TreeDomainModel {

  var comparator: Comparator<in NodeDescriptor<*>>? = null

  private val semaphore = Semaphore(concurrency)

  override suspend fun computeRoot(): TreeNodeDomainModel? = accessData {
    structure.rootElement.validate()?.toValidNodeModel(null)
  }

  private suspend fun <T> accessData(accessor: () -> T): T = semaphore.withPermit {
    if (useReadAction) {
      readAction {
        accessor()
      }
    }
    else {
      blockingContext {
        accessor()
      }
    }
  }

  private fun Any.validate(): Any? {
    if (this is AbstractTreeNode<*> && value == null) return null
    if (this is ValidateableNode && !isValid) return null
    if (!structure.isValid(this)) return null
    return this
  }

  private fun Any.toValidNodeModel(parentDescriptor: NodeDescriptor<*>?): TreeStructureNodeDomainModel? {
    val validElement = validate() ?: return null
    val descriptor = structure.createDescriptor(validElement, parentDescriptor)
    descriptor.update()
    return TreeStructureNodeDomainModel(descriptor)
  }

  private inner class TreeStructureNodeDomainModel(private val userObject: NodeDescriptor<*>) : TreeNodeDomainModel, LegacyCompatibilityTreeNode {
    override fun getUserObject(): Any = userObject

    override suspend fun computeIsLeaf(): Boolean = accessData {
      val element = userObject.element
      when (structure.getLeafState(element)) {
        LeafState.ALWAYS -> true
        LeafState.NEVER -> false
        else -> element.validate()?.let { structure.getChildElements(it).isEmpty() } != false
      }
    }

    override suspend fun computePresentation(builder: TreeNodePresentationBuilder): Flow<TreeNodePresentation> =
      flowOf(accessData {
        buildPresentation(builder, userObject)
      })

    override suspend fun computeChildren(): List<TreeNodeDomainModel> = accessData {
      val comparator = comparator
      val parentElement = userObject.element.validate() ?: return@accessData emptyList()
      val result = SmartList<TreeStructureNodeDomainModel>()
      val childElements = structure.getChildElements(parentElement)
      for (childElement in childElements) {
        val validChild = childElement.toValidNodeModel(userObject) ?: continue
        result.add(validChild)
      }
      if (comparator != null) {
        result.sortWith(Comparator.comparing({ it.userObject }, comparator))
      }
      return@accessData result
    }

    override fun toString(): String {
      return "TreeStructureNodeDomainModel(userObject=$userObject)"
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as TreeStructureNodeDomainModel

      return userObject == other.userObject
    }

    override fun hashCode(): Int {
      return userObject.hashCode()
    }
  }
}

internal fun buildPresentation(builder: TreeNodePresentationBuilder, userObject: Any): TreeNodePresentation {
  if (userObject !is PresentableNodeDescriptor<*>) {
    builder.setMainText(userObject.toString())
    return builder.build()
  }
  userObject.update()
  val colorScheme = EditorColorsManager.getInstance().schemeForCurrentUITheme
  val presentation = userObject.presentation
  return builder.run {
    setIcon(presentation.getIcon(false))
    val mainText = presentation.presentableText ?: ""
    setMainText(mainText)
    val locationAttributes: SimpleTextAttributes
    // all this mess has been ported from NodeRenderer
    val forcedColor = presentation.forcedTextForeground
    if (presentation.coloredText.isEmpty()) {
      val color = forcedColor ?: userObject.color
      val attributes = getSimpleTextAttributes(presentation, color, userObject, colorScheme)
      appendTextFragment(mainText, attributes)
      locationAttributes = SimpleTextAttributes.merge(attributes, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
    else {
      var first = true
      for (fragment in presentation.coloredText) {
        var attributes = fragment.attributes ?: SimpleTextAttributes.REGULAR_ATTRIBUTES
        if (attributes.fgColor == null && forcedColor != null) {
          attributes = addColorToSimpleTextAttributes(attributes, forcedColor)
        }
        if (first) {
          val attributesKey = presentation.textAttributesKey
          if (attributesKey != null) {
            val forcedAttributes = colorScheme.getAttributes(attributesKey)
            if (forcedAttributes != null) {
              attributes = SimpleTextAttributes.merge(attributes, SimpleTextAttributes.fromTextAttributes(forcedAttributes))
            }
          }
          first = false
        }
        appendTextFragment(fragment.text, attributes)
      }
      locationAttributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
    }
    val location = presentation.locationString
    if (!location.isNullOrEmpty()) {
      val prefix = presentation.locationPrefix
      val suffix = presentation.locationSuffix
      appendTextFragment(prefix + location + suffix, locationAttributes)
    }
    setToolTipText(presentation.tooltip)
    build()
  }
}

private fun getSimpleTextAttributes(presentation: PresentationData, color: Color?, node: Any, scheme: EditorColorsScheme): SimpleTextAttributes {
  val simpleTextAttributes = getSimpleTextAttributes(presentation, scheme)
  return addColorToSimpleTextAttributes(simpleTextAttributes, color)
}

private fun addColorToSimpleTextAttributes(simpleTextAttributes: SimpleTextAttributes, color: Color?): SimpleTextAttributes {
  var simpleTextAttributes = simpleTextAttributes
  var color = color
  if (color != null) {
    val textAttributes = simpleTextAttributes.toTextAttributes()
    if (simpleTextAttributes.useFaded()) {
      color = ColorUtil.faded(color)
    }
    textAttributes.foregroundColor = color
    simpleTextAttributes = SimpleTextAttributes.fromTextAttributes(textAttributes)
  }
  return simpleTextAttributes
}

private fun getSimpleTextAttributes(
  presentation: PresentationData,
  colorsScheme: EditorColorsScheme,
): SimpleTextAttributes {
  val textAttributesKey = presentation.textAttributesKey
  if (textAttributesKey == null) return SimpleTextAttributes.REGULAR_ATTRIBUTES
  val textAttributes = colorsScheme.getAttributes(textAttributesKey)
  return if (textAttributes == null) SimpleTextAttributes.REGULAR_ATTRIBUTES else SimpleTextAttributes.fromTextAttributes(textAttributes)
}
