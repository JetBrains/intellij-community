// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.toolWindow

import com.intellij.openapi.components.*
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.DesktopLayout
import com.intellij.openapi.wm.impl.UnifiedToolWindowWeights
import com.intellij.openapi.wm.impl.WindowInfoImpl
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.openapi.wm.safeToolWindowPaneId
import com.intellij.ui.ExperimentalUI
import kotlinx.serialization.Serializable
import java.awt.Rectangle

@Service(Service.Level.APP)
@State(name = "ToolWindowLayout", storages = [
  Storage(value = "window.layouts.xml"),
  Storage(value = "window.state.xml", deprecated = true, roamingType = RoamingType.DISABLED),
])
class ToolWindowDefaultLayoutManager(private val isNewUi: Boolean)
  : PersistentStateComponentWithModificationTracker<ToolWindowDefaultLayoutManager.ToolWindowLayoutStorageManagerState> {
  companion object {
    @JvmStatic
    fun getInstance(): ToolWindowDefaultLayoutManager = service()

    const val INITIAL_LAYOUT_NAME = "Custom"
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

  fun getLayoutCopy(): DesktopLayout = state.getActiveLayoutCopy(isNewUi)

  fun setLayout(layout: DesktopLayout) = setLayout(activeLayoutName, layout)

  fun setLayout(name: String, layout: DesktopLayout) {
    tracker.incModificationCount()
    val list = layout.getSortedList().map(::convertWindowStateToDescriptor)
    val weights = convertUnifiedWeightsToDescriptor(layout.unifiedWeights)
    state = state.withUpdatedLayout(name, list, isNewUi, weights)
  }

  fun renameLayout(oldName: String, newName: String) {
    tracker.incModificationCount()
    state = state.withRenamedLayout(oldName, newName)
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
        setLayout(INITIAL_LAYOUT_NAME, it)
        return
      }
    }
    loadDefaultLayout(isNewUi)
  }

  private fun loadDefaultLayout(isNewUi: Boolean) {
    state = state.withUpdatedLayout(INITIAL_LAYOUT_NAME, getDefaultLayoutToolWindowDescriptors(isNewUi), isNewUi)
  }

  override fun loadState(state: ToolWindowLayoutStorageManagerState) {
    val newState = if (state.layouts.isEmpty() && (state.v1.isNotEmpty() || state.v2.isNotEmpty())) { // migrating from 2022.3
      ToolWindowLayoutStorageManagerState(layouts = mapOf(INITIAL_LAYOUT_NAME to LayoutDescriptor(v1 = state.v1, v2 = state.v2)))
    }
    else {
      state
    }
    this.state = newState
    setLayout(newState.activeLayoutName, newState.getActiveLayoutCopy(isNewUi))
  }

  /**
   * Rider uses default layout for per-app toolwindows feature, so we need to migrate default layout
   */
  @Serializable
  data class ToolWindowLayoutStorageManagerStateV1(
    val v1: List<ToolWindowDescriptor> = emptyList(),
    val v2: List<ToolWindowDescriptor> = emptyList()
  )

  @Serializable
  data class ToolWindowLayoutStorageManagerState(
    val activeLayoutName: String = INITIAL_LAYOUT_NAME,
    val layouts: Map<String, LayoutDescriptor> = emptyMap(),
    val v1: List<ToolWindowDescriptor> = emptyList(),
    val v2: List<ToolWindowDescriptor> = emptyList(),
  ) {

    fun getActiveLayoutCopy(isNewUi: Boolean): DesktopLayout {
      return DesktopLayout(
        convertWindowDescriptorsToWindowInfos(getDescriptors(isNewUi)),
        convertUnifiedWeightsDescriptorToUnifiedWeights(getUnifiedWeights())
      )
    }

    private fun getDescriptors(isNewUi: Boolean): List<ToolWindowDescriptor> =
      (layouts[activeLayoutName]?.let { if (isNewUi) it.v2 else it.v1 } ?: emptyList())
        .ifEmpty { getDefaultLayoutToolWindowDescriptors(isNewUi) }

    private fun getUnifiedWeights(): Map<String, Float> =
        layouts[activeLayoutName]?.unifiedWeights ?: DEFAULT_UNIFIED_WEIGHTS_DESCRIPTOR

    fun withRenamedLayout(oldName: String, newName: String): ToolWindowLayoutStorageManagerState =
      copy(
        activeLayoutName = if (oldName == activeLayoutName) newName else activeLayoutName,
        layouts = layouts - oldName + (newName to layouts.getValue(oldName))
      )

    fun withUpdatedLayout(
      name: String,
      layout: List<ToolWindowDescriptor>,
      isNewUi: Boolean,
      weights: Map<String, Float> = DEFAULT_UNIFIED_WEIGHTS_DESCRIPTOR,
    ): ToolWindowLayoutStorageManagerState =
        copy(
          activeLayoutName = name,
          layouts = layouts + (name to layouts[name].withUpdatedLayout(layout, isNewUi, weights))
        )

    fun withoutLayout(name: String) = copy(layouts = layouts - name)

  }

  @Serializable
  data class LayoutDescriptor(
    val v1: List<ToolWindowDescriptor> = emptyList(),
    val v2: List<ToolWindowDescriptor> = emptyList(),
    val unifiedWeights: Map<String, Float> = DEFAULT_UNIFIED_WEIGHTS_DESCRIPTOR,
  ) {
    fun withUpdatedLayout(
      layout: List<ToolWindowDescriptor>,
      isNewUi: Boolean,
      weights: Map<String, Float>,
    ): LayoutDescriptor =
      if (isNewUi) copy(v2 = layout, unifiedWeights = weights) else copy(v1 = layout, unifiedWeights = weights)
  }

}

private fun getDefaultLayoutToolWindowDescriptors(isNewUi: Boolean): List<ToolWindowDescriptor> {
  val builder = DefaultToolWindowLayoutBuilderImpl()
  for (layoutExtension in DefaultToolWindowLayoutExtension.EP_NAME.extensionList) {
    if (isNewUi) {
      layoutExtension.buildV2Layout(builder)
    }
    else {
      layoutExtension.buildV1Layout(builder)
    }
  }
  return builder.build()
}

private val DEFAULT_UNIFIED_WEIGHTS_DESCRIPTOR: Map<String, Float> = ToolWindowAnchor.VALUES.associate { it.toString() to WindowInfoImpl.DEFAULT_WEIGHT }

fun ToolWindowDefaultLayoutManager.LayoutDescriptor?.withUpdatedLayout(
  layout: List<ToolWindowDescriptor>,
  isNewUi: Boolean,
  weights: Map<String, Float>,
): ToolWindowDefaultLayoutManager.LayoutDescriptor =
  (this ?: ToolWindowDefaultLayoutManager.LayoutDescriptor()).withUpdatedLayout(layout, isNewUi, weights)

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
    internalType = it.internalType,
    contentUiType = when(it.contentUiType) {
      ToolWindowContentUiType.TABBED -> ToolWindowDescriptor.ToolWindowContentUiType.TABBED
      ToolWindowContentUiType.COMBO -> ToolWindowDescriptor.ToolWindowContentUiType.COMBO
      else -> throw IllegalStateException("Unsupported contentUiType ${it.contentUiType}")
    },
  )
}

private fun convertUnifiedWeightsToDescriptor(unifiedToolWindowWeights: UnifiedToolWindowWeights): Map<String, Float> =
  ToolWindowAnchor.VALUES.associate { anchor ->
    anchor.toString() to unifiedToolWindowWeights[anchor]
  }

private fun convertWindowDescriptorsToWindowInfos(list: List<ToolWindowDescriptor>): MutableMap<String, WindowInfoImpl> {
  return list.associateTo(hashMapOf()) { it.id to
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
      internalType = it.internalType
      contentUiType = when (it.contentUiType) {
        ToolWindowDescriptor.ToolWindowContentUiType.TABBED -> ToolWindowContentUiType.TABBED
        ToolWindowDescriptor.ToolWindowContentUiType.COMBO -> ToolWindowContentUiType.COMBO
      }
    }
  }
}

private fun convertUnifiedWeightsDescriptorToUnifiedWeights(unifiedWeightsDescriptor: Map<String, Float>) = UnifiedToolWindowWeights().apply {
  top = unifiedWeightsDescriptor[ToolWindowAnchor.TOP.toString()] ?: WindowInfoImpl.DEFAULT_WEIGHT
  left = unifiedWeightsDescriptor[ToolWindowAnchor.LEFT.toString()] ?: WindowInfoImpl.DEFAULT_WEIGHT
  bottom = unifiedWeightsDescriptor[ToolWindowAnchor.BOTTOM.toString()] ?: WindowInfoImpl.DEFAULT_WEIGHT
  right = unifiedWeightsDescriptor[ToolWindowAnchor.RIGHT.toString()] ?: WindowInfoImpl.DEFAULT_WEIGHT
}
