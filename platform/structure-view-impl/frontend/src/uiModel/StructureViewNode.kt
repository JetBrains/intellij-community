// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend.uiModel

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.PathElementIdProvider
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.vcs.FileStatus
import com.intellij.platform.structureView.impl.dto.StructureViewTreeElementDto
import com.intellij.platform.structureView.impl.dto.toPresentation
import com.intellij.platform.structureView.impl.uiModel.StructureUiTreeElement
import com.intellij.ui.icons.RowIcon
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.TestOnly
import java.util.Collections
import java.util.Enumeration
import javax.swing.Icon
import javax.swing.tree.TreeNode

/**
 * confined to the EDT together with the rest of the UI state
 * see [StructureUiModelImpl].
 */
internal class StructureViewNode : StructureUiTreeElement, PathElementIdProvider, Queryable {
  private var dto: StructureViewTreeElementDto? = null
  private var myPresentation: ItemPresentation = EMPTY_PRESENTATION

  internal var parentNode: StructureViewNode? = null

  // sourceChildren is the reusable backend graph. projectedChildren caches action-applied topology,
  // visibleChildren is the current Swing projection after narrow-down / speed search.
  override val sourceChildren: MutableList<StructureViewNode> = mutableListOf()
  override val projectedChildren: MutableList<StructureUiTreeElement> = mutableListOf()
  override val visibleChildren: MutableList<StructureUiTreeElement> = mutableListOf()

  override val id: Int
    get() = dto?.id ?: ROOT_ID

  override val parent: StructureUiTreeElement?
    get() = parentNode

  override val indexInParent: Int
    get() = dto?.index ?: 0

  override val presentation: ItemPresentation
    get() = myPresentation

  override val speedSearchText: String?
    get() = dto?.speedSearchText

  override val alwaysShowPlus: Boolean
    get() = dto?.alwaysShowsPlus ?: false

  override val alwaysLeaf: Boolean
    get() = dto?.alwaysLeaf ?: false

  override val shouldAutoExpand: Boolean
    get() = dto?.autoExpand ?: false

  override val fileStatus: FileStatus
    get() = FileStatus.NOT_CHANGED

  override val filterResults: List<Boolean>
    get() = dto?.filterResults ?: emptyList()

  @RequiresEdt
  internal fun update(dto: StructureViewTreeElementDto) {
    this.dto = dto
    myPresentation = dto.presentation.toPresentation()
  }

  override fun getChildAt(childIndex: Int): TreeNode {
    return visibleChildren[childIndex]
  }

  override fun getChildCount(): Int {
    return visibleChildren.size
  }

  override fun getParent(): TreeNode? {
    return parentNode
  }

  override fun getIndex(node: TreeNode): Int {
    return visibleChildren.indexOf(node)
  }

  override fun getAllowsChildren(): Boolean {
    return !alwaysLeaf
  }

  override fun isLeaf(): Boolean {
    return alwaysLeaf || (!alwaysShowPlus && visibleChildren.isEmpty())
  }

  override fun children(): Enumeration<out TreeNode> {
    return Collections.enumeration(visibleChildren)
  }

  override fun getPathElementId(): String {
    return id.toString()
  }

  override fun equals(other: Any?): Boolean {
    return other is StructureUiTreeElement && id == other.id
  }

  override fun hashCode(): Int {
    return id
  }

  override fun toString(): String {
    return speedSearchText ?: presentation.presentableText ?: ""
  }

  @TestOnly
  override fun putInfo(info: MutableMap<in String, in String?>) {
    info["text"] = presentation.presentableText
    info["location"] = presentation.locationString
    info["icon"] = with(presentation.getIcon(false)) {
      (this as? RowIcon)?.allIcons?.joinToString(transform = Icon::toString) ?: this?.toString()
    }
  }

  companion object {
    private const val ROOT_ID = 0
    private val EMPTY_PRESENTATION = PresentationData("", "", null, null)
  }
}
