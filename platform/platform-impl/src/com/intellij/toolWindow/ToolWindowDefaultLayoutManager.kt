// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.toolWindow

import com.intellij.openapi.components.*
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.safeToolWindowPaneId
import com.intellij.ui.ExperimentalUI
import kotlinx.serialization.Serializable
import java.awt.Rectangle

@Service(Service.Level.APP)
@State(name = "ToolWindowLayout", storages = [Storage(value = "window.state.xml", roamingType = RoamingType.DISABLED)])
class ToolWindowDefaultLayoutManager(private val isNewUi: Boolean)
  : PersistentStateComponentWithModificationTracker<ToolWindowDefaultLayoutManager.ToolWindowLayoutStorageManagerState> {
  companion object {
    @JvmStatic
    fun getInstance(): ToolWindowDefaultLayoutManager = service()

    const val DEFAULT_LAYOUT_NAME = "Default"
  }

  @Volatile
  private var state = ToolWindowLayoutStorageManagerState()
  private val tracker = SimpleModificationTracker()

  var activeLayoutName: String
    get() = state.activeLayoutName
    set(value) {
      tracker.incModificationCount()
      state = state.copy(activeLayoutName = value)
    }

  @Suppress("unused")
  constructor() : this(ExperimentalUI.isNewUI())

  fun getLayoutNames(): Set<String> = state.layouts.keys

  fun getLayoutCopy(): DesktopLayout = state.getActiveLayoutCopy(isNewUi) ?: DesktopLayout()

  fun setLayout(layout: DesktopLayout) = setLayout(activeLayoutName, layout)

  fun setLayout(name: String, layout: DesktopLayout) {
    tracker.incModificationCount()
    val list = layout.getSortedList().map(::convertWindowStateToDescriptor)
    state = state.withUpdatedLayout(name, list, isNewUi)
  }

  fun deleteLayout(name: String) {
    tracker.incModificationCount()
    state = state.withoutLayout(name)
  }

  override fun getState() = state

  override fun getStateModificationCount() = tracker.modificationCount

  override fun noStateLoaded() {
    if (!isNewUi) {
      (WindowManager.getInstance() as? WindowManagerImpl)?.oldLayout?.let {
        setLayout(DEFAULT_LAYOUT_NAME, it)
        return
      }
    }
    loadDefaultLayout(isNewUi)
  }

  private fun loadDefaultLayout(isNewUi: Boolean) {
    val provider = service<DefaultToolWindowLayoutProvider>()
    val list = if (isNewUi) provider.createV2Layout() else provider.createV1Layout()
    state = state.withUpdatedLayout(DEFAULT_LAYOUT_NAME, list, isNewUi)
  }

  override fun loadState(state: ToolWindowLayoutStorageManagerState) {
    this.state = state
    val activeLayout = state.getActiveLayoutCopy(isNewUi)
    if (activeLayout == null) {
      loadDefaultLayout(isNewUi)
    } else {
      setLayout(state.activeLayoutName, activeLayout)
    }
  }

  @Serializable
  data class ToolWindowLayoutStorageManagerState(
    val activeLayoutName: String = DEFAULT_LAYOUT_NAME,
    val layouts: Map<String, LayoutDescriptor> = emptyMap(),
  ) {

    fun getActiveLayoutCopy(isNewUi: Boolean): DesktopLayout? {
      val activeLayoutDescriptors = getDescriptors(isNewUi)
      if (activeLayoutDescriptors.isEmpty()) {
        return null
      }
      return convertDescriptorListToLayout(activeLayoutDescriptors)
    }

    private fun getDescriptors(isNewUi: Boolean): List<ToolWindowDescriptor> =
        layouts[activeLayoutName]?.let { if (isNewUi) it.v2 else it.v1 } ?: emptyList()

    fun withUpdatedLayout(
      name: String,
      layout: List<ToolWindowDescriptor>,
      isNewUi: Boolean
    ): ToolWindowLayoutStorageManagerState =
        copy(
          activeLayoutName = name,
          layouts = layouts + (name to layouts[name].withUpdatedLayout(layout, isNewUi))
        )

    fun withoutLayout(name: String) = copy(layouts = layouts - name)

  }

  @Serializable
  data class LayoutDescriptor(
    val v1: List<ToolWindowDescriptor> = emptyList(),
    val v2: List<ToolWindowDescriptor> = emptyList(),
  ) {
    fun withUpdatedLayout(layout: List<ToolWindowDescriptor>, isNewUi: Boolean): LayoutDescriptor =
      if (isNewUi) copy(v2 = layout) else copy(v1 = layout)
  }

}

fun ToolWindowDefaultLayoutManager.LayoutDescriptor?.withUpdatedLayout(
  layout: List<ToolWindowDescriptor>,
  isNewUi: Boolean
): ToolWindowDefaultLayoutManager.LayoutDescriptor =
  (this ?: ToolWindowDefaultLayoutManager.LayoutDescriptor()).withUpdatedLayout(layout, isNewUi)

private fun convertWindowStateToDescriptor(it: WindowInfoImpl): ToolWindowDescriptor {
  return ToolWindowDescriptor(
    id = it.id!!,
    order = it.order,

    paneId = it.safeToolWindowPaneId,
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

      toolWindowPaneId = it.paneId
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