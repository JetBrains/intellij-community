// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.impl.dto

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Serializable
data class StructureViewTreeElementDto(
  val id: Int,
  val parentId: Int,
  val index: Int,
  val speedSearchText: String?,
  val valueHashCode: Int,
  val presentation: PresentationDataDto,
  val autoExpand: Boolean,
  val alwaysShowsPlus: Boolean,
  val alwaysLeaf: Boolean,
  val filterResults: List<Boolean>,
)
