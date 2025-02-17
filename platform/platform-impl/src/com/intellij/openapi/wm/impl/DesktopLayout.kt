// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.wm.impl

import com.intellij.configurationStore.jdomSerializer
import com.intellij.configurationStore.serialize
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.wm.*
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@Internal
class DesktopLayout(
  private val idToInfo: MutableMap<String, WindowInfoImpl> = HashMap(),
  val unifiedWeights: UnifiedToolWindowWeights = UnifiedToolWindowWeights(),
) {
  companion object {
    @NonNls const val TAG: String = "layout"
  }

  /**
   * @param paneId the ID of the tool window pane that this anchor is attached to
   * @param anchor anchor of the stripe.
   * @return the next available ordinal number in the specified stripe. Returns `0` if there is no tool window with the specified anchor.
   */
  internal fun getNextOrder(paneId: String, anchor: ToolWindowAnchor): Int {
    return idToInfo.values.asSequence().filter { paneId == it.safeToolWindowPaneId && anchor == it.anchor }.maxOfOrNull { it.order }?.plus(1) ?: 0
  }

  fun copy(): DesktopLayout = DesktopLayout(
    idToInfo.entries.associateTo(HashMap(idToInfo.size)) { e -> e.key to e.value.copy() },
    unifiedWeights.copy(),
  )

  internal fun create(task: RegisterToolWindowTaskData): WindowInfoImpl {
    val info = WindowInfoImpl()
    info.id = task.id
    info.isFromPersistentSettings = false
    info.isSplit = task.sideTool

    info.toolWindowPaneId = WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
    info.anchor = task.anchor

    task.contentFactory?.anchor?.let {
      info.anchor = it
    }
    return info
  }

  fun getInfo(id: String): WindowInfoImpl? = idToInfo.get(id)
  fun getInfos(): Map<String, WindowInfoImpl> = idToInfo.toMap()

  internal fun addInfo(id: String, info: WindowInfoImpl) {
    val old = idToInfo.put(id, info)
    LOG.assertTrue(old == null)
  }

  fun getUnifiedAnchorWeight(anchor: ToolWindowAnchor): Float = unifiedWeights[anchor]

  fun setUnifiedAnchorWeight(anchor: ToolWindowAnchor, weight: Float) {
    unifiedWeights[anchor] = weight
  }

  /**
   * Sets new `anchor` and `id` for the specified tool window.
   * Also, the method properly updates the order of all other tool windows.
   */
  fun setAnchor(info: WindowInfoImpl,
                newPaneId: String,
                newAnchor: ToolWindowAnchor,
                suppliedNewOrder: Int): List<WindowInfoImpl> {
    var newOrder = suppliedNewOrder
    val affected = ArrayList<WindowInfoImpl>()

    // if order isn't defined, then the window will be the last in the stripe
    if (newOrder == -1) {
      newOrder = getNextOrder(newPaneId, newAnchor)
    }
    else {
      // shift order to the right in the target stripe
      for (otherInfo in idToInfo.values) {
        if (otherInfo !== info && otherInfo.safeToolWindowPaneId == newPaneId && otherInfo.anchor == newAnchor
            && otherInfo.order != -1 && otherInfo.order >= newOrder) {
          otherInfo.order++
          affected.add(otherInfo)
        }
      }
    }

    info.order = newOrder
    info.toolWindowPaneId = newPaneId
    info.anchor = newAnchor
    return affected
  }

  fun readExternal(layoutElement: Element, isFromPersistentSettings: Boolean = true) {
    val infoBinding = jdomSerializer.getBeanBinding(WindowInfoImpl::class.java)

    val list = mutableListOf<WindowInfoImpl>()
    for (element in layoutElement.getChildren(WindowInfoImpl.TAG)) {
      val info = WindowInfoImpl()
      info.isFromPersistentSettings = isFromPersistentSettings
      infoBinding.deserializeInto(info, element)
      info.normalizeAfterRead()
      val id = info.id
      if (id == null) {
        LOG.warn("Skip invalid window info (no id): ${JDOMUtil.writeElement(element)}")
        continue
      }

      idToInfo.put(id, info)
      list.add(info)
    }

    val unifiedWeightsElement = layoutElement.getChild(UnifiedToolWindowWeights.TAG)
    if (unifiedWeightsElement != null) {
      jdomSerializer.deserializeInto(unifiedWeights, unifiedWeightsElement)
    }

    normalizeOrder(list)
    for (info in list) {
      info.resetModificationCount()
    }
    unifiedWeights.resetModificationCount()
  }

  val stateModificationCount: Long
    get() {
      if (idToInfo.isEmpty()) {
        return 0
      }

      var result = 0L
      for (info in idToInfo.values) {
        result += info.modificationCount
      }
      return result
    }

  fun writeExternal(tagName: String): Element? {
    if (idToInfo.isEmpty()) {
      return null
    }

    var state: Element? = null
    for (info in getSortedList()) {
      val child = serialize(info) ?: continue
      if (state == null) {
        state = Element(tagName)
      }
      state.addContent(child)
    }
    if (state != null) {
      val serializedUnifiedWeights = serialize(unifiedWeights)
      if (serializedUnifiedWeights != null) {
        state.addContent(serializedUnifiedWeights)
      }
    }
    return state
  }

  internal fun getSortedList(): List<WindowInfoImpl> = idToInfo.values.sortedWith(windowInfoComparator)

  override fun toString(): String =
    "DesktopLayout(\n" +
      "unifiedWeights: $unifiedWeights,\n" +
      idToInfo.entries.joinToString("\n") { "${it.key}: (${it.value})" } +
    "\n)"
}

private val LOG = logger<DesktopLayout>()

private fun getAnchorWeight(anchor: ToolWindowAnchor): Int {
  return when (anchor) {
    ToolWindowAnchor.TOP -> 1
    ToolWindowAnchor.LEFT -> 2
    ToolWindowAnchor.BOTTOM -> 3
    ToolWindowAnchor.RIGHT -> 4
    else -> 0
  }
}

internal val windowInfoComparator: Comparator<WindowInfo> = Comparator { o1, o2 ->
  val weightDiff = getAnchorWeight(o1.anchor) - getAnchorWeight(o2.anchor)
  if (weightDiff != 0) weightDiff else o1.order - o2.order
}

/**
 * Normalizes the order of windows in the array. Order of a first window will be `0`.
 */
internal fun normalizeOrder(list: MutableList<WindowInfoImpl>) {
  list.sortWith(windowInfoComparator)
  var order = 0
  var lastAnchor = ToolWindowAnchor.TOP
  for (info in list) {
    if (info.order == -1) {
      continue
    }

    if (lastAnchor != info.anchor) {
      lastAnchor = info.anchor
      order = 0
    }

    info.order = order++
  }
}
