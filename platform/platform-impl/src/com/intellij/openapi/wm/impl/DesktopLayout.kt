// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.configurationStore.serialize
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.util.xmlb.XmlSerializer
import gnu.trove.THashMap
import org.jdom.Element
import java.util.*
import javax.swing.SwingConstants

class DesktopLayout {
  companion object {
    const val TAG = "layout"
  }

  /**
   * Map between `id`s `WindowInfo`s.
   */
  private val idToInfo = THashMap<String, WindowInfoImpl>()

  fun copy(): DesktopLayout {
    val result = DesktopLayout()
    result.idToInfo.ensureCapacity(idToInfo.size)
    idToInfo.forEachEntry { id, info ->
      val newInfo = info.copy()
      result.idToInfo.put(id, newInfo)
      true
    }
    return result
  }

  private fun normalizeOrders() {
    normalizeOrder(getAllInfos(ToolWindowAnchor.TOP))
    normalizeOrder(getAllInfos(ToolWindowAnchor.LEFT))
    normalizeOrder(getAllInfos(ToolWindowAnchor.BOTTOM))
    normalizeOrder(getAllInfos(ToolWindowAnchor.RIGHT))
  }

  /**
   * Creates or gets `WindowInfo` for the specified `id`. If tool
   * window is being registered first time the method uses `anchor`.
   */
  fun getOrCreate(task: RegisterToolWindowTask): WindowInfoImpl {
    var info = idToInfo.get(task.id)
    if (info == null) {
      info = WindowInfoImpl()
      info.isFromPersistentSettings = false
      info.id = task.id
      info.anchor = task.anchor
      info.isSplit = task.sideTool
      idToInfo.put(task.id, info)
    }
    return info
  }

  /**
   * @return `WindowInfo` for the window with specified `id`.
   * If `onlyRegistered` is `true` then returns not `null`
   * value if and only if window with `id` is registered one.
   */
  fun getInfo(id: String, onlyRegistered: Boolean): WindowInfoImpl? {
    val info = idToInfo.get(id) ?: return null
    return if (onlyRegistered) null else info
  }

  fun getInfo(id: String) = idToInfo.get(id)

  /**
   * @return all (registered and not unregistered) `WindowInfos` for the specified `anchor`.
   * Returned infos are sorted by order.
   */
  internal fun getAllInfos(anchor: ToolWindowAnchor): List<WindowInfoImpl> {
    val result = mutableListOf<WindowInfoImpl>()
    for (info in idToInfo.values) {
      if (anchor == info.anchor) {
        result.add(info)
      }
    }
    result.sortWith(windowInfoComparator)
    return result
  }

  /**
   * @param anchor anchor of the stripe.
   * @return maximum ordinal number in the specified stripe. Returns `-1`
   * if there is no any tool window with the specified anchor.
   */
  private fun getMaxOrder(anchor: ToolWindowAnchor): Int {
    var res = -1
    for (info in idToInfo.values) {
      if (anchor == info.anchor && res < info.order) {
        res = info.order
      }
    }
    return res
  }

  /**
   * Sets new `anchor` and `id` for the specified tool window.
   * Also the method properly updates order of all other tool windows.
   */
  fun setAnchor(id: String, newAnchor: ToolWindowAnchor, suppliedNewOrder: Int) {
    var newOrder = suppliedNewOrder
    // if order isn't defined then the window will the last in the stripe
    if (newOrder == -1) {
      newOrder = getMaxOrder(newAnchor) + 1
    }

    val info = getInfo(id)
    val oldAnchor = info!!.anchor
    // shift order to the right in the target stripe
    val infos = getAllInfos(newAnchor)
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
    normalizeOrder(getAllInfos(oldAnchor))
    if (oldAnchor != newAnchor) {
      normalizeOrder(getAllInfos(newAnchor))
    }
  }

  fun setSplitMode(id: String, split: Boolean) {
    getInfo(id)!!.isSplit = split
  }

  fun readExternal(layoutElement: Element) {
    val infoBinding = XmlSerializer.getBeanBinding(WindowInfoImpl::class.java)

    for (element in layoutElement.getChildren(WindowInfoImpl.TAG)) {
      val info = WindowInfoImpl()
      infoBinding.deserializeInto(info, element)
      info.normalizeAfterRead()
      val id = info.id
      if (id == null) {
        LOG.warn("Skip invalid window info (no id): " + JDOMUtil.writeElement(element))
        continue
      }
      idToInfo.put(id, info)
    }

    for (info in idToInfo.values) {
      // if order isn't defined then window's button will be the last one in the stripe
      if (info.order == -1) {
        info.order = getMaxOrder(info.anchor) + 1
      }
    }
    normalizeOrders()
  }

  val stateModificationCount: Long
    get() {
      if (idToInfo.isEmpty) {
        return 0
      }

      var result = 0L
      for (info in idToInfo.values) {
        result += info.modificationCount
      }
      return result
    }

  fun writeExternal(tagName: String): Element? {
    if (idToInfo.isEmpty) {
      return null
    }

    val list = idToInfo.values.toMutableList()
    list.sortedWith(windowInfoComparator)
    val state = Element(tagName)
    for (info in list) {
      val element = serialize(info)
      if (element != null) {
        state.addContent(element)
      }
    }
    return state
  }

  internal inner class MyStripeButtonComparator(anchor: ToolWindowAnchor, manager: ToolWindowManagerImpl) : Comparator<StripeButton> {
    private val idToInfo: MutableMap<String?, WindowInfoImpl> = THashMap()

    override fun compare(obj1: StripeButton, obj2: StripeButton): Int {
      val info1 = idToInfo.get(obj1.windowInfo.id)
      val order1 = info1?.order ?: 0
      val info2 = idToInfo.get(obj2.windowInfo.id)
      val order2 = info2?.order ?: 0
      return order1 - order2
    }

    init {
      for (info in idToInfo.values) {
        if (anchor == info.anchor && manager.isToolWindowRegistered(info.id ?: continue)) {
          idToInfo.put(info.id, info.copy())
        }
      }
    }
  }
}

private val LOG = logger<DesktopLayout>()

private fun getAnchorWeight(anchor: ToolWindowAnchor): Int {
  return when (anchor) {
    ToolWindowAnchor.TOP -> SwingConstants.TOP
    ToolWindowAnchor.LEFT -> SwingConstants.LEFT
    ToolWindowAnchor.BOTTOM -> SwingConstants.BOTTOM
    else -> if (anchor == ToolWindowAnchor.RIGHT) SwingConstants.RIGHT else 0
  }
}

private val windowInfoComparator = Comparator { o1: WindowInfoImpl, o2: WindowInfoImpl ->
  val d = getAnchorWeight(o1.anchor) - getAnchorWeight(o2.anchor)
  if (d == 0) o1.order - o2.order else d
}

/**
 * Normalizes order of windows in the passed array. Note, that array should be
 * sorted by order (by ascending). Order of first window will be `0`.
 */
private fun normalizeOrder(infos: List<WindowInfoImpl>) {
  for (i in infos.indices) {
    infos[i].order = i
  }
}