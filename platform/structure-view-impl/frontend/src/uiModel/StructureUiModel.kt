// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend.uiModel

import com.intellij.openapi.Disposable
import com.intellij.platform.structureView.impl.dto.StructureViewModelDto
import com.intellij.platform.structureView.impl.uiModel.StructureUiTreeElement
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CompletableFuture

interface StructureUiModel: Disposable {
  val dto: StructureViewModelDto?
  val rootElement: StructureUiTreeElement
  val smartExpand: Boolean
  val minimumAutoExpandDepth: Int
  val editorSelection: StateFlow<StructureUiTreeElement?>

  fun isActionEnabled(action: StructureTreeAction): Boolean

  fun setActionEnabled(action: StructureTreeAction, isEnabled: Boolean, isAutoClicked: Boolean)

  fun getActions(): Collection<StructureTreeAction>

  fun navigateTo(element: StructureUiTreeElement?): CompletableFuture<Boolean>

  @TestOnly
  suspend fun getNewSelection(): Int?

  fun getUpdatePendingFlow(): StateFlow<Boolean>

  fun addListener(listener: StructureUiModelListener)
}


interface StructureUiModelListener {
  fun onTreeChanged()
  fun onActionsChanged()
}