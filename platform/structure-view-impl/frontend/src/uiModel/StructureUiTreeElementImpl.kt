// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend.uiModel

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.vcs.FileStatus
import com.intellij.platform.structureView.impl.dto.StructureViewTreeElementDto
import com.intellij.platform.structureView.impl.dto.toPresentation
import com.intellij.platform.structureView.impl.uiModel.StructureUiTreeElement
import com.intellij.ui.icons.RowIcon
import org.jetbrains.annotations.TestOnly
import javax.swing.Icon

open class StructureUiTreeElementImpl(val dto: StructureViewTreeElementDto) : StructureUiTreeElement, Queryable {
  override val id: Int
    get() = dto.id

  override var parent: StructureUiTreeElement? = null
    internal set

  override val indexInParent: Int = dto.index

  override val presentation: ItemPresentation
    get() = dto.presentation.toPresentation()

  override val speedSearchText: String?
    get() = dto.speedSearchText

  override val alwaysShowPlus: Boolean
    get() = dto.alwaysShowsPlus

  override val alwaysLeaf: Boolean
    get() = dto.alwaysLeaf

  override val shouldAutoExpand: Boolean
    get() = dto.autoExpand

  override val fileStatus: FileStatus
    get() = FileStatus.NOT_CHANGED

  override val filterResults: List<Boolean>
    get() = dto.filterResults

  internal val myChildren = mutableListOf<StructureUiTreeElementImpl>()

  override val children: List<StructureUiTreeElement> get() = myChildren

  override fun equals(other: Any?): Boolean {
    return other is StructureUiTreeElement && id == other.id
  }

  override fun hashCode(): Int {
    return id
  }

  override fun toString(): String {
    return "StructureUiTreeElementImpl(dto=$dto)"
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
    fun StructureViewTreeElementDto.toUiElement(parent: StructureUiTreeElement?): StructureUiTreeElementImpl {
      return StructureUiTreeElementImpl(this).also { it.parent = parent }
    }
  }
}