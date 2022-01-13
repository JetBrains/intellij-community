// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.wm.impl

import com.intellij.configurationStore.serialize
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WindowInfo
import com.intellij.ui.ExperimentalUI
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.annotations.NonNls

class DesktopLayout(private val idToInfo: MutableMap<String, WindowInfoImpl> = HashMap()) {
  companion object {
    @NonNls internal const val TAG = "layout"
  }

  fun copy(): DesktopLayout {
    val map = HashMap<String, WindowInfoImpl>(idToInfo.size)
    for (entry in idToInfo) {
      map.put(entry.key, entry.value.copy())
    }
    return DesktopLayout(map)
  }

  /**
   * Creates or gets `WindowInfo` for the specified `id`.
   */
  internal fun getOrCreate(task: RegisterToolWindowTask): WindowInfoImpl {
    return idToInfo.getOrPut(task.id) {
      val info = createDefaultInfo(task.id)
      info.anchor = task.anchor
      info.largeStripeAnchor = task.anchor
      info.isSplit = task.sideTool
      info.isSplit = task.sideTool
      info
    }
  }

  private fun createDefaultInfo(id: String): WindowInfoImpl {
    val info = WindowInfoImpl()
    info.id = id
    info.isFromPersistentSettings = false
    info.order = getMaxOrder(idToInfo.values, info.anchor) + 1
    info.orderOnLargeStripe = getMaxOrderForLargeStripe(idToInfo.values, info.largeStripeAnchor) + 1
    return info
  }

  fun getInfo(id: String) = idToInfo.get(id)

  internal fun addInfo(id: String, info: WindowInfoImpl) {
    val old = idToInfo.put(id, info)
    LOG.assertTrue(old == null)
  }

  /**
   * Sets new `anchor` and `id` for the specified tool window.
   * Also, the method properly updates order of all other tool windows.
   */
  fun setAnchor(info: WindowInfoImpl,
                newAnchor: ToolWindowAnchor,
                suppliedNewOrder: Int,
                isNewUi: Boolean = ExperimentalUI.isNewToolWindowsStripes()) {
    var newOrder = suppliedNewOrder
    // if order isn't defined then the window will the last in the stripe
    if (newOrder == -1) {
      newOrder = getMaxOrder(idToInfo.values, newAnchor) + 1
    }

    val oldAnchor = if (isNewUi) info.largeStripeAnchor else info.anchor
    // shift order to the right in the target stripe
    val infos = getAllInfos(idToInfo.values, newAnchor)
    if (isNewUi) {
      infos.asReversed().forEach { if (newOrder <= it.orderOnLargeStripe) it.orderOnLargeStripe++ }
      info.orderOnLargeStripe = newOrder
      info.largeStripeAnchor = newAnchor
      info.isVisibleOnLargeStripe = true
    }
    else {
      infos.asReversed().forEach { if (newOrder <= it.order) it.order++ }
      info.order = newOrder
      info.anchor = newAnchor
    }

    // normalize orders in the source and target stripes
    normalizeOrder(getAllInfos(idToInfo.values, oldAnchor))
    if (oldAnchor != newAnchor) {
      normalizeOrder(getAllInfos(idToInfo.values, newAnchor))
    }
  }

  fun readExternal(layoutElement: Element, isNewUi: Boolean, isFromPersistentSettings: Boolean = true) {
    val infoBinding = XmlSerializer.getBeanBinding(WindowInfoImpl::class.java)

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

      // if order isn't defined then window's button will be the last one in the stripe
      if (info.order == -1) {
        info.order = getMaxOrder(list, info.anchor) + 1
      }
      if (info.orderOnLargeStripe == -1) {
        info.order = getMaxOrderForLargeStripe(list, info.largeStripeAnchor) + 1
      }

      if (info.isSplit && isNewUi) {
        info.isSplit = false
      }

      idToInfo.put(id, info)
      list.add(info)
    }

    normalizeOrder(getAllInfos(list, ToolWindowAnchor.TOP))
    normalizeOrder(getAllInfos(list, ToolWindowAnchor.LEFT))
    normalizeOrder(getAllInfos(list, ToolWindowAnchor.BOTTOM))
    normalizeOrder(getAllInfos(list, ToolWindowAnchor.RIGHT))

    for (info in list) {
      info.resetModificationCount()
    }
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

    val state = Element(tagName)
    for (info in idToInfo.values.sortedWith(windowInfoComparator)) {
      serialize(info)?.let {
        state.addContent(it)
      }
    }
    return state
  }
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

internal val windowInfoComparator: Comparator<WindowInfo> =
  if (ExperimentalUI.isNewToolWindowsStripes())
    Comparator { o1, o2 ->
      val weightDiff = getAnchorWeight(o1.largeStripeAnchor) - getAnchorWeight(o2.largeStripeAnchor)
      if (weightDiff != 0) weightDiff else o1.orderOnLargeStripe - o2.orderOnLargeStripe
    }
  else
    Comparator { o1, o2 ->
      val weightDiff = getAnchorWeight(o1.anchor) - getAnchorWeight(o2.anchor)
      if (weightDiff != 0) weightDiff else o1.order - o2.order
  }

/**
 * Normalizes order of windows in the passed array. Note, that array should be
 * sorted by order (by ascending). Order of first window will be `0`.
 */
private fun normalizeOrder(infos: List<WindowInfoImpl>) {
  if (ExperimentalUI.isNewToolWindowsStripes()) {
    for (i in infos.indices) {
      infos.get(i).orderOnLargeStripe = i
    }
  }
  else {
    for (i in infos.indices) {
      infos.get(i).order = i
    }
  }
}

/**
 * @param anchor anchor of the stripe.
 * @return maximum ordinal number in the specified stripe. Returns `-1` if there is no tool window with the specified anchor.
 */
private fun getMaxOrder(list: Collection<WindowInfoImpl>, anchor: ToolWindowAnchor): Int {
  return list.asSequence().filter { anchor == it.anchor }.maxOfOrNull { it.order } ?: -1
}


private fun getMaxOrderForLargeStripe(list: Collection<WindowInfoImpl>, anchor: ToolWindowAnchor): Int {
  // orderOnLargeStripe maybe specified even if isVisibleOnLargeStripe is false
  // - we preserve orderOnLargeStripe or it can be specified as part of a default layout
  return list.asSequence()
           .filter { it.orderOnLargeStripe != -1 && anchor == it.largeStripeAnchor }
           .maxOfOrNull { it.orderOnLargeStripe } ?: -1
}

/**
 * @return all (registered and not unregistered) `WindowInfos` for the specified `anchor`.
 * Returned infos are sorted by order.
 */
private fun getAllInfos(list: Collection<WindowInfoImpl>, anchor: ToolWindowAnchor): List<WindowInfoImpl> {
  return if (ExperimentalUI.isNewToolWindowsStripes()) {
    list.filter { anchor == it.largeStripeAnchor }.sortedWith(windowInfoComparator)
  }
  else {
    list.filter { anchor == it.anchor }.sortedWith(windowInfoComparator)
  }
}
