// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.wm.impl

import com.intellij.configurationStore.serialize
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WindowInfo
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@Internal
class DesktopLayout(private val idToInfo: MutableMap<String, WindowInfoImpl> = HashMap()) {
  companion object {
    @NonNls internal const val TAG = "layout"
  }

  constructor(descriptors: List<WindowInfoImpl>) : this(descriptors.associateByTo(HashMap()) { it.id!! })

  fun copy(): DesktopLayout {
    val map = HashMap<String, WindowInfoImpl>(idToInfo.size)
    for (entry in idToInfo) {
      map.put(entry.key, entry.value.copy())
    }
    return DesktopLayout(map)
  }

  internal fun create(task: RegisterToolWindowTask, isNewUi: Boolean): WindowInfoImpl {
    val info = WindowInfoImpl()
    info.id = task.id
    info.isFromPersistentSettings = false
    // we must allocate order - otherwise, on drag-n-drop, we cannot move some tool windows to the end
    // because sibling's order is equal to -1, so, always in the end
    info.order = getMaxOrder(idToInfo.values, task.anchor)
    if (isNewUi) {
      info.isShowStripeButton = false
    }
    else {
      info.isSplit = task.sideTool
    }

    info.anchor = task.anchor

    task.contentFactory?.anchor?.let {
      info.anchor = it
    }

    val oldInfo = idToInfo.put(task.id, info)
    assert(oldInfo == null)
    return info
  }

  internal fun remove(info: WindowInfoImpl) {
    val removed = idToInfo.remove(info.id, info)
    assert(removed)
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
                suppliedNewOrder: Int): List<WindowInfoImpl> {
    var newOrder = suppliedNewOrder
    val affected = ArrayList<WindowInfoImpl>()

    // if order isn't defined then the window will be the last in the stripe
    if (newOrder == -1) {
      newOrder = getMaxOrder(idToInfo.values, newAnchor) + 1
    }
    else {
      // shift order to the right in the target stripe
      for (otherInfo in idToInfo.values) {
        if (otherInfo !== info && otherInfo.anchor == newAnchor && otherInfo.order != -1 && otherInfo.order >= newOrder) {
          otherInfo.order++
          affected.add(otherInfo)
        }
      }
    }

    info.order = newOrder
    info.anchor = newAnchor
    return affected
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

      if (info.isSplit && isNewUi) {
        info.isSplit = false
      }

      idToInfo.put(id, info)
      list.add(info)
    }

    normalizeOrder(list)
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
    for (info in getSortedList()) {
      serialize(info)?.let {
        state.addContent(it)
      }
    }
    return state
  }

  internal fun getSortedList(): List<WindowInfoImpl> = idToInfo.values.sortedWith(windowInfoComparator)
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
 * Normalizes order of windows in the array. Order of first window will be `0`.
 */
private fun normalizeOrder(list: MutableList<WindowInfoImpl>) {
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

/**
 * @param anchor anchor of the stripe.
 * @return maximum ordinal number in the specified stripe. Returns `-1` if there is no tool window with the specified anchor.
 */
private fun getMaxOrder(list: Collection<WindowInfoImpl>, anchor: ToolWindowAnchor): Int {
  return list.asSequence().filter { anchor == it.anchor }.maxOfOrNull { it.order } ?: -1
}