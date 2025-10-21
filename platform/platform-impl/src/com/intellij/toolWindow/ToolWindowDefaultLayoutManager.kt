// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

@Service(Service.Level.APP)
@State(name = "ToolWindowLayout", category = SettingsCategory.UI, storages = [
  Storage(value = "window.layouts.xml"),
  Storage(value = "window.state.xml", deprecated = true, roamingType = RoamingType.DISABLED),
])
class ToolWindowDefaultLayoutManager(private val isNewUi: Boolean)
  : PersistentStateComponentWithModificationTracker<ToolWindowLayoutStorageManagerState> {
  companion object {
    @JvmStatic
    fun getInstance(): ToolWindowDefaultLayoutManager = service()

    const val INITIAL_LAYOUT_NAME: String = "Custom"
    const val FACTORY_DEFAULT_LAYOUT_NAME: String = ""
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

  fun getFactoryDefaultLayoutCopy(): DesktopLayout = state.getLayoutCopy(FACTORY_DEFAULT_LAYOUT_NAME, isNewUi)

  fun setLayout(layout: DesktopLayout) {
    setLayout(activeLayoutName, layout)
  }

  fun setLayout(name: String, layout: DesktopLayout) {
    tracker.incModificationCount()
    var layoutName = name
    if (layoutName == FACTORY_DEFAULT_LAYOUT_NAME) {
      // Saving over the factory default layout makes it non-default.
      // This shouldn't normally happen because we validate the name in the platform.
      // But it could happen if it's an external call, e.g., from Rider.
      layoutName = INITIAL_LAYOUT_NAME
    }
    val list = layout.getSortedList().map(::convertWindowStateToDescriptor)
    val weights = convertUnifiedWeightsToDescriptor(layout.unifiedWeights)
    state = state.withUpdatedLayout(layoutName, list, isNewUi, weights)
  }

  fun renameLayout(oldName: String, newName: String) {
    tracker.incModificationCount()
    state = state.withRenamedLayout(oldName, newName)
  }

  fun deleteLayout(name: String) {
    tracker.incModificationCount()
    state = state.withoutLayout(name)
  }

  @ApiStatus.Internal
  override fun getState(): ToolWindowLayoutStorageManagerState = state

  @ApiStatus.Internal
  override fun getStateModificationCount(): Long = tracker.modificationCount

  @ApiStatus.Internal
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

  @ApiStatus.Internal
  override fun loadState(state: ToolWindowLayoutStorageManagerState) {
    val newState = if (state.layouts.isEmpty() && (state.v1.isNotEmpty() || state.v2.isNotEmpty())) { // migrating from 2022.3
      ToolWindowLayoutStorageManagerState(layouts = mapOf(INITIAL_LAYOUT_NAME to ToolWindowLayoutDescriptor(v1 = state.v1, v2 = state.v2)))
    }
    else {
      state.withoutLayout(FACTORY_DEFAULT_LAYOUT_NAME) // Just in case the storage is corrupted and actually has an empty name.
    }
    this.state = newState
    if (newState.activeLayoutName != FACTORY_DEFAULT_LAYOUT_NAME) {
      setLayout(newState.activeLayoutName, newState.getActiveLayoutCopy(isNewUi))
    }
  }
}

/**
 * Rider uses default layout for per-app toolwindows feature, so we need to migrate the default layout
 */
@Serializable
@ApiStatus.Internal
data class ToolWindowLayoutStorageManagerStateV1(
  val v1: List<ToolWindowDescriptor> = emptyList(),
  val v2: List<ToolWindowDescriptor> = emptyList()
)

@Serializable
@ApiStatus.Internal
data class ToolWindowLayoutStorageManagerState(
  val activeLayoutName: String = ToolWindowDefaultLayoutManager.INITIAL_LAYOUT_NAME,
  val layouts: Map<String, ToolWindowLayoutDescriptor> = emptyMap(),
  val v1: List<ToolWindowDescriptor> = emptyList(),
  val v2: List<ToolWindowDescriptor> = emptyList(),
) {

  fun getActiveLayoutCopy(isNewUi: Boolean): DesktopLayout = getLayoutCopy(activeLayoutName, isNewUi)

  fun getLayoutCopy(layoutName: String, isNewUi: Boolean): DesktopLayout {
    return DesktopLayout(
      convertWindowDescriptorsToWindowInfos(getDescriptors(layoutName, isNewUi)),
      convertUnifiedWeightsDescriptorToUnifiedWeights(getUnifiedWeights(layoutName))
    )
  }

  private fun getDescriptors(layoutName: String, isNewUi: Boolean): List<ToolWindowDescriptor> {
    return (layouts.get(layoutName)?.let { if (isNewUi) it.v2 else it.v1 } ?: emptyList())
      .ifEmpty { getDefaultLayoutToolWindowDescriptors(isNewUi) }
  }

  private fun getUnifiedWeights(layoutName: String): Map<String, Float> {
    return layouts.get(layoutName)?.unifiedWeights ?: DEFAULT_UNIFIED_WEIGHTS_DESCRIPTOR
  }

  fun withRenamedLayout(oldName: String, newName: String): ToolWindowLayoutStorageManagerState {
    return copy(
      activeLayoutName = if (oldName == activeLayoutName) newName else activeLayoutName,
      layouts = layouts - oldName + (newName to layouts.getValue(oldName))
    )
  }

  fun withUpdatedLayout(
    name: String,
    layout: List<ToolWindowDescriptor>,
    isNewUi: Boolean,
    weights: Map<String, Float> = DEFAULT_UNIFIED_WEIGHTS_DESCRIPTOR,
  ): ToolWindowLayoutStorageManagerState {
    return copy(
      activeLayoutName = name,
      layouts = layouts + (name to withUpdatedLayout(layoutDescriptor = layouts.get(name), layout = layout, isNewUi = isNewUi, weights = weights))
    )
  }

  fun withoutLayout(name: String): ToolWindowLayoutStorageManagerState = copy(layouts = layouts - name)
}

@Serializable
@ApiStatus.Internal
data class ToolWindowLayoutDescriptor(
  val v1: List<ToolWindowDescriptor> = emptyList(),
  val v2: List<ToolWindowDescriptor> = emptyList(),
  val unifiedWeights: Map<String, Float> = DEFAULT_UNIFIED_WEIGHTS_DESCRIPTOR,
) {
  fun withUpdatedLayout(
    layout: List<ToolWindowDescriptor>,
    isNewUi: Boolean,
    weights: Map<String, Float>,
  ): ToolWindowLayoutDescriptor {
    return if (isNewUi) copy(v2 = layout, unifiedWeights = weights) else copy(v1 = layout, unifiedWeights = weights)
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

private val DEFAULT_UNIFIED_WEIGHTS_DESCRIPTOR = ToolWindowAnchor.VALUES.associate { it.toString() to WindowInfoImpl.DEFAULT_WEIGHT }

private fun withUpdatedLayout(
  layoutDescriptor: ToolWindowLayoutDescriptor?,
  layout: List<ToolWindowDescriptor>,
  isNewUi: Boolean,
  weights: Map<String, Float>,
): ToolWindowLayoutDescriptor {
  return (layoutDescriptor ?: ToolWindowLayoutDescriptor()).withUpdatedLayout(layout = layout, isNewUi = isNewUi, weights = weights)
}

private fun convertWindowStateToDescriptor(it: WindowInfoImpl): ToolWindowDescriptor {
  return ToolWindowDescriptor(
    id = it.id!!,
    order = it.order,

    paneId = it.safeToolWindowPaneId,
    anchor = when (it.anchor) {
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
    contentUiType = when (it.contentUiType) {
      ToolWindowContentUiType.TABBED -> ToolWindowDescriptor.ToolWindowContentUiType.TABBED
      ToolWindowContentUiType.COMBO -> ToolWindowDescriptor.ToolWindowContentUiType.COMBO
      else -> throw IllegalStateException("Unsupported contentUiType ${it.contentUiType}")
    },
  )
}

private fun convertUnifiedWeightsToDescriptor(unifiedToolWindowWeights: UnifiedToolWindowWeights): Map<String, Float> {
  return ToolWindowAnchor.VALUES.associate { anchor ->
    anchor.toString() to unifiedToolWindowWeights.get(anchor)
  }
}

private fun convertWindowDescriptorsToWindowInfos(list: List<ToolWindowDescriptor>): MutableMap<String, WindowInfoImpl> {
  val result = list.associateTo(hashMapOf()) { descriptor ->
    descriptor.id to WindowInfoImpl().apply {
      id = descriptor.id
      order = descriptor.order

      toolWindowPaneId = descriptor.paneId
      anchor = when (descriptor.anchor) {
        ToolWindowDescriptor.ToolWindowAnchor.TOP -> ToolWindowAnchor.TOP
        ToolWindowDescriptor.ToolWindowAnchor.LEFT -> ToolWindowAnchor.LEFT
        ToolWindowDescriptor.ToolWindowAnchor.BOTTOM -> ToolWindowAnchor.BOTTOM
        ToolWindowDescriptor.ToolWindowAnchor.RIGHT -> ToolWindowAnchor.RIGHT
      }
      isAutoHide = descriptor.isAutoHide
      floatingBounds = descriptor.floatingBounds?.let { Rectangle(it.get(0), it.get(1), it.get(2), it.get(3)) }
      isMaximized = descriptor.isMaximized

      isActiveOnStart = descriptor.isActiveOnStart
      isVisible = descriptor.isVisible
      isShowStripeButton = descriptor.isShowStripeButton

      weight = descriptor.weight
      sideWeight = descriptor.sideWeight

      isSplit = descriptor.isSplit

      type = descriptor.type
      internalType = descriptor.internalType
      contentUiType = when (descriptor.contentUiType) {
        ToolWindowDescriptor.ToolWindowContentUiType.TABBED -> ToolWindowContentUiType.TABBED
        ToolWindowDescriptor.ToolWindowContentUiType.COMBO -> ToolWindowContentUiType.COMBO
      }
    }
  }
  val windowInfoList = result.values.toMutableList()
  normalizeOrder(windowInfoList)
  return result
}

private fun convertUnifiedWeightsDescriptorToUnifiedWeights(unifiedWeightsDescriptor: Map<String, Float>): UnifiedToolWindowWeights {
  return UnifiedToolWindowWeights().apply {
    top = unifiedWeightsDescriptor[ToolWindowAnchor.TOP.toString()] ?: WindowInfoImpl.DEFAULT_WEIGHT
    left = unifiedWeightsDescriptor[ToolWindowAnchor.LEFT.toString()] ?: WindowInfoImpl.DEFAULT_WEIGHT
    bottom = unifiedWeightsDescriptor[ToolWindowAnchor.BOTTOM.toString()] ?: WindowInfoImpl.DEFAULT_WEIGHT
    right = unifiedWeightsDescriptor[ToolWindowAnchor.RIGHT.toString()] ?: WindowInfoImpl.DEFAULT_WEIGHT
  }
}
