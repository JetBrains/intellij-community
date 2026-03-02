// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.impl.uiModel

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.vcs.FileStatus

interface StructureUiTreeElement {
  val id: Int
  val parent: StructureUiTreeElement?
  val indexInParent: Int
  val presentation: ItemPresentation
  val speedSearchText: String?
  val alwaysShowPlus: Boolean
  val alwaysLeaf: Boolean
  val shouldAutoExpand: Boolean
  val fileStatus: FileStatus
  val filterResults: List<Boolean>
  val children: List<StructureUiTreeElement>
}