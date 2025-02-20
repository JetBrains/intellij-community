// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical

import com.intellij.ide.structureView.StructureViewBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class StructureViewTab(
  val title: String,
) {
  PHYSICAL(StructureViewBundle.message("structureview.tab.physical")),
  LOGICAL(StructureViewBundle.message("structureview.tab.logical"));

  companion object {
    fun ofTitle(title: String): StructureViewTab? = entries.firstOrNull { it.title == title }
  }

  fun not(): StructureViewTab {
    return when (this) {
      PHYSICAL -> LOGICAL
      LOGICAL -> PHYSICAL
    }
  }
}