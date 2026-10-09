// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.wm.safeToolWindowPaneId

/**
 * Seeds [layout] with the factory-default entry of every id in [ids] that [layout] lacks and [factoryDefault] has.
 *
 * A seeded entry keeps the anchor, the split, the weights, the content UI type and the stripe-button flag of the
 * factory default. It is not visible: the seed adds a stripe button, it never opens a window. The entry goes
 * directly after the nearest preceding factory-default neighbour that [layout] holds on the same pane, anchor and
 * split. Without such a neighbour it goes last on that stripe.
 *
 * An entry that [layout] already holds wins, including one whose stripe button the user removed.
 *
 * Returns every info whose order changed, the seeded ones included.
 */
internal fun seedFromFactoryDefault(layout: DesktopLayout, ids: Collection<String>, factoryDefault: DesktopLayout): List<WindowInfoImpl> {
  val missing = ids.asSequence()
    .distinct()
    .filter { layout.getInfo(it) == null }
    .mapNotNull { factoryDefault.getInfo(it) }
    .sortedWith(windowInfoComparator)
    .toList()
  if (missing.isEmpty()) {
    return emptyList()
  }

  val affected = ArrayList<WindowInfoImpl>()
  for (default in missing) {
    val id = default.id ?: continue
    val info = default.copy()
    info.isVisible = false
    affected.addAll(layout.setAnchor(info, info.safeToolWindowPaneId, info.anchor, seededOrder(layout, factoryDefault, default)))
    layout.addInfo(id, info)
    affected.add(info)
  }
  return affected
}

/** The order directly after the nearest preceding default neighbour that [layout] holds in place, or `-1` for the end of the stripe. */
private fun seededOrder(layout: DesktopLayout, factoryDefault: DesktopLayout, default: WindowInfoImpl): Int {
  if (default.order < 0) {
    return -1
  }
  val predecessor = factoryDefault.getInfos().values.asSequence()
    .filter { it.id != default.id && it.isNeighbourOf(default) && it.order in 0 until default.order }
    .sortedByDescending { it.order }
    .mapNotNull { layout.getInfo(it.id ?: return@mapNotNull null) }
    .firstOrNull { it.isNeighbourOf(default) && it.order >= 0 }
  return predecessor?.order?.plus(1) ?: -1
}

private fun WindowInfoImpl.isNeighbourOf(other: WindowInfoImpl): Boolean {
  return safeToolWindowPaneId == other.safeToolWindowPaneId && anchor == other.anchor && isSplit == other.isSplit
}
