// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logical.impl

import com.intellij.ide.TypePresentationService
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.smartTree.ActionPresentation
import com.intellij.ide.util.treeView.smartTree.Group
import com.intellij.ide.util.treeView.smartTree.Grouper
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import javax.swing.Icon

class LogicalGrouper: Grouper {

  private val typePresentationService = TypePresentationService.getService()

  override fun getPresentation(): ActionPresentation {
    return object : ActionPresentation {
      override fun getText(): String = "Logical"

      override fun getDescription(): String = ""

      override fun getIcon(): Icon? = null
    }
  }

  override fun getName(): String = "Logical"

  override fun group(parent: AbstractTreeNode<*>, children: MutableCollection<TreeElement>): Collection<Group> {
    val structureElement = parent.value as? LogicalStructureViewTreeElement<*> ?: return emptyList()
    val result = mutableListOf<Group>()
    for (pair in structureElement.getLogicalAssembledModel().getChildrenGrouped()) {
      result.add(object: Group {
        override fun getPresentation(): ItemPresentation {
          val groupingObject = pair.first
          return PresentationData(
            typePresentationService.getObjectName(groupingObject),
            typePresentationService.getTypeName(groupingObject),
            typePresentationService.getIcon(groupingObject),
            null
          )
        }

        override fun getChildren(): Collection<TreeElement> {
          return pair.second.mapNotNull { childLogical ->
            children.firstOrNull { it is LogicalStructureViewTreeElement<*> && it.getLogicalAssembledModel().model == childLogical.model }
          }
        }
      })
    }
    return result
  }
}