// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.ide.util.treeView.*
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.blockingContext
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.TreeDomainModel
import com.intellij.ui.treeStructure.TreeNodeDomainModel
import com.intellij.ui.treeStructure.TreeNodePresentation
import com.intellij.ui.treeStructure.TreeNodePresentationBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class TreeStructureDomainModelAdapter(
  private val structure: AbstractTreeStructure,
  private val useReadAction: Boolean,
  concurrency: Int,
) : TreeDomainModel {
  private val semaphore = Semaphore(concurrency)

  override suspend fun computeRoot(): TreeNodeDomainModel? = accessData {
    structure.rootElement.validate()?.let { TreeStructureNodeDomainModel(structure.createDescriptor(it, null)) }
  }

  override suspend fun <T> accessData(accessor: () -> T): T = semaphore.withPermit {
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

  private inner class TreeStructureNodeDomainModel(override val userObject: NodeDescriptor<*>) : TreeNodeDomainModel {
    override suspend fun computeLeafState(): LeafState = accessData {
      structure.getLeafState(userObject.element)
    }

    override suspend fun computePresentation(builder: TreeNodePresentationBuilder): Flow<TreeNodePresentation> =
      flowOf(accessData {
        buildPresentation(builder, userObject)
      })

    override suspend fun computeChildren(): List<TreeNodeDomainModel> = accessData {
      userObject.element.validate()?.let { parentElement ->
        structure.getChildElements(parentElement)
          .asSequence()
          .mapNotNull { it.validate() }
          .map { childElement ->
            TreeStructureNodeDomainModel(structure.createDescriptor(childElement, userObject))
          }
          .toList()
      } ?: emptyList()
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
  val presentation = userObject.presentation
  return builder.run {
    setIcon(presentation.getIcon(false))
    setMainText(presentation.presentableText ?: "")
    for (fragment in presentation.coloredText) {
      appendTextFragment(fragment.text, fragment.attributes ?: SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
    val location = presentation.locationString
    if (!location.isNullOrEmpty()) {
      val prefix = presentation.locationPrefix
      val suffix = presentation.locationSuffix
      appendTextFragment(prefix + location + suffix, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
    setToolTipText(presentation.tooltip)
    build()
  }
}
