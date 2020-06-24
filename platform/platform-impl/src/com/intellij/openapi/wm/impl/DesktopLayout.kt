// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.configurationStore.serialize
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WindowInfo
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import java.util.*

class DesktopLayout(private val idToInfo: MutableMap<String, WindowInfoImpl> = HashMap<String, WindowInfoImpl>()) {
  companion object {
    internal const val TAG = "layout"
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
      info.isSplit = task.sideTool
      info
    }
  }

  private fun createDefaultInfo(id: String): WindowInfoImpl {
    val info = WindowInfoImpl()
    info.id = id
    info.isFromPersistentSettings = false
    info.order = getMaxOrder(idToInfo.values, info.anchor) + 1
    return info
  }

  fun getInfo(id: String) = idToInfo.get(id)

  internal fun addInfo(id: String, info: WindowInfoImpl) {
    val old = idToInfo.put(id, info)
    LOG.assertTrue(old == null)
  }

  /**
   * Sets new `anchor` and `id` for the specified tool window.
   * Also the method properly updates order of all other tool windows.
   */
  fun setAnchor(info: WindowInfoImpl, newAnchor: ToolWindowAnchor, suppliedNewOrder: Int) {
    var newOrder = suppliedNewOrder
    // if order isn't defined then the window will the last in the stripe
    if (newOrder == -1) {
      newOrder = getMaxOrder(idToInfo.values, newAnchor) + 1
    }

    val oldAnchor = info.anchor
    // shift order to the right in the target stripe
    val infos = getAllInfos(idToInfo.values, newAnchor)
    for (i in infos.size - 1 downTo -1 + 1) {
      val info2 = infos[i]
      if (newOrder <= info2.order) {
        info2.order = info2.order + 1
      }
    }

    // "move" window into the target position
    info.anchor = newAnchor
    info.order = newOrder
    // normalize orders in the source and target stripes
    normalizeOrder(getAllInfos(idToInfo.values, oldAnchor))
    if (oldAnchor != newAnchor) {
      normalizeOrder(getAllInfos(idToInfo.values, newAnchor))
    }
  }

  fun readExternal(layoutElement: Element) {
    val infoBinding = XmlSerializer.getBeanBinding(WindowInfoImpl::class.java)

    val list = mutableListOf<WindowInfoImpl>()
    for (element in layoutElement.getChildren(WindowInfoImpl.TAG)) {
      val info = WindowInfoImpl()
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

internal val windowInfoComparator: Comparator<WindowInfo> = Comparator { o1, o2 ->
  val anchorWeight = getAnchorWeight(o1.anchor) - getAnchorWeight(o2.anchor)
  if (anchorWeight == 0) o1.order - o2.order else anchorWeight
}

/**
 * Normalizes order of windows in the passed array. Note, that array should be
 * sorted by order (by ascending). Order of first window will be `0`.
 */
private fun normalizeOrder(infos: List<WindowInfoImpl>) {
  for (i in infos.indices) {
    infos.get(i).order = i
  }
}

/**
 * @param anchor anchor of the stripe.
 * @return maximum ordinal number in the specified stripe. Returns `-1`
 * if there is no any tool window with the specified anchor.
 */
private fun getMaxOrder(list: Collection<WindowInfoImpl>, anchor: ToolWindowAnchor): Int {
  var result = -1
  for (info in list) {
    if (anchor == info.anchor && result < info.order) {
      result = info.order
    }
  }
  return result
}

/**
 * @return all (registered and not unregistered) `WindowInfos` for the specified `anchor`.
 * Returned infos are sorted by order.
 */
private fun getAllInfos(list: Collection<WindowInfoImpl>, anchor: ToolWindowAnchor): List<WindowInfoImpl> {
  val result = mutableListOf<WindowInfoImpl>()
  for (info in list) {
    if (anchor == info.anchor) {
      result.add(info)
    }
  }
  result.sortWith(windowInfoComparator)
  return result
}
