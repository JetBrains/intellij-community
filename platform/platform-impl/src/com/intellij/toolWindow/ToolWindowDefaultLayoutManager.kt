// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.toolWindow

import com.intellij.openapi.components.*
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.*
import com.intellij.ui.ExperimentalUI
import kotlinx.serialization.Serializable
import java.awt.Rectangle

@Service(Service.Level.APP)
@State(name = "ToolWindowLayout", storages = [Storage(value = "window.state.xml", roamingType = RoamingType.DISABLED)])
internal class ToolWindowDefaultLayoutManager(private val isNewUi: Boolean)
  : PersistentStateComponentWithModificationTracker<ToolWindowDefaultLayoutManager.ToolWindowLayoutStorageManagerState> {
  companion object {
    @JvmStatic
    fun getInstance(): ToolWindowDefaultLayoutManager = service()
  }

  @Volatile
  private var state = ToolWindowLayoutStorageManagerState()
  private val tracker = SimpleModificationTracker()

  // default layout
  private var layout = DesktopLayout()

  @Suppress("unused")
  private constructor() : this(ExperimentalUI.isNewUI())

  fun getLayoutCopy() = layout.copy()

  fun setLayout(layout: DesktopLayout) {
    this.layout = layout.copy()
    tracker.incModificationCount()
    val list = layout.getSortedList().map(::convertWindowStateToDescriptor)
    state = if (ExperimentalUI.isNewUI()) state.copy(v2 = list) else state.copy(v1 = list)
  }

  override fun getState() = state

  override fun getStateModificationCount() = tracker.modificationCount

  @Suppress("DuplicatedCode")
  override fun noStateLoaded() {
    if (!isNewUi) {
      (WindowManager.getInstance() as? WindowManagerImpl)?.oldLayout?.let {
        setLayout(it)
        return
      }
    }

    loadDefaultLayout(isNewUi)
  }

  private fun loadDefaultLayout(isNewUi: Boolean) {
    val provider = service<DefaultToolWindowLayoutProvider>()
    val list = if (isNewUi) provider.createV2Layout() else provider.createV1Layout()
    layout = convertDescriptorListToLayout(list)
    state = if (isNewUi) state.copy(v2 = list) else state.copy(v1 = list)
  }

  override fun loadState(state: ToolWindowLayoutStorageManagerState) {
    this.state = state

    if (if (isNewUi) state.v2.isEmpty() else state.v1.isEmpty()) {
      loadDefaultLayout(isNewUi)
    }
  }

  @Serializable
  data class ToolWindowLayoutStorageManagerState(
    val v1: List<ToolWindowDescriptor> = emptyList(),
    val v2: List<ToolWindowDescriptor> = emptyList(),
  )
}

private fun convertWindowStateToDescriptor(it: WindowInfoImpl): ToolWindowDescriptor {
  return ToolWindowDescriptor(
    id = it.id!!,
    order = it.order,

    anchor = when(it.anchor) {
      ToolWindowAnchor.TOP -> ToolWindowDescriptor.ToolWindowAnchor.TOP
      ToolWindowAnchor.LEFT -> ToolWindowDescriptor.ToolWindowAnchor.LEFT
      ToolWindowAnchor.BOTTOM -> ToolWindowDescriptor.ToolWindowAnchor.BOTTOM
      ToolWindowAnchor.RIGHT -> ToolWindowDescriptor.ToolWindowAnchor.RIGHT
      else -> throw IllegalStateException("Unsupported anchor ${it.anchor}")
    },
    isAutoHide = it.isAutoHide,
    floatingBounds = it.floatingBounds?.let { listOf(it.x, it.y, it.width, it.height) },
    isMaximized = it.isMaximized,

    isActiveOnStart = it.isActiveOnStart,
    isVisible = it.isVisible,
    isShowStripeButton = it.isShowStripeButton,

    weight = it.weight,
    sideWeight = it.sideWeight,

    isSplit = it.isSplit,

    type = it.type,
    internalType = it.type,
    contentUiType = when(it.contentUiType) {
      ToolWindowContentUiType.TABBED -> ToolWindowDescriptor.ToolWindowContentUiType.TABBED
      ToolWindowContentUiType.COMBO -> ToolWindowDescriptor.ToolWindowContentUiType.COMBO
      else -> throw IllegalStateException("Unsupported contentUiType ${it.contentUiType}")
    },
  )
}

@Suppress("DuplicatedCode")
private fun convertDescriptorListToLayout(list: List<ToolWindowDescriptor>): DesktopLayout {
  return DesktopLayout(list.map {
    WindowInfoImpl().apply {
      id = it.id
      order = it.order

      anchor = when (it.anchor) {
        ToolWindowDescriptor.ToolWindowAnchor.TOP -> ToolWindowAnchor.TOP
        ToolWindowDescriptor.ToolWindowAnchor.LEFT -> ToolWindowAnchor.LEFT
        ToolWindowDescriptor.ToolWindowAnchor.BOTTOM -> ToolWindowAnchor.BOTTOM
        ToolWindowDescriptor.ToolWindowAnchor.RIGHT -> ToolWindowAnchor.RIGHT
      }
      isAutoHide = it.isAutoHide
      floatingBounds = it.floatingBounds?.let { Rectangle(it.get(0), it.get(1), it.get(2), it.get(3)) }
      isMaximized = it.isMaximized

      isActiveOnStart = it.isActiveOnStart
      isVisible = it.isVisible
      isShowStripeButton = it.isShowStripeButton

      weight = it.weight
      sideWeight = it.sideWeight

      isSplit = it.isSplit

      type = it.type
      internalType = it.type
      contentUiType = when (it.contentUiType) {
        ToolWindowDescriptor.ToolWindowContentUiType.TABBED -> ToolWindowContentUiType.TABBED
        ToolWindowDescriptor.ToolWindowContentUiType.COMBO -> ToolWindowContentUiType.COMBO
      }
    }
  })
}