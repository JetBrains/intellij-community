// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap

import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapParsingListener
import com.intellij.util.containers.ContainerUtil

internal interface SoftWrapChangeNotifier {
  fun notifySoftWrapsChanged()
  fun notifySoftWrapRecalculationEnds()
}

internal class SoftWrapNotifier : SoftWrapChangeNotifier {
  private val softWrapChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList<SoftWrapChangeListener>()
  private val softWrapParsingListeners = ContainerUtil.createLockFreeCopyOnWriteList<SoftWrapParsingListener>()

  // region SoftWrapChangeListener
  fun addSoftWrapChangeListener(listener: SoftWrapChangeListener): Boolean = softWrapChangeListeners.add(listener)

  override fun notifySoftWrapsChanged() = softWrapChangeListeners.forEach { it.softWrapsChanged() }

  override fun notifySoftWrapRecalculationEnds() = softWrapChangeListeners.forEach { it.recalculationEnds() }
  // endregion

  // region SoftWrapParsingListener
  fun addSoftWrapParsingListener(listener: SoftWrapParsingListener): Boolean = softWrapParsingListeners.add(listener)

  fun removeSoftWrapParsingListener(listener: SoftWrapParsingListener): Boolean = softWrapParsingListeners.remove(listener)

  fun notifyAllDirtyRegionsReparsed() = softWrapParsingListeners.forEach { it.onAllDirtyRegionsReparsed() }

  fun notifyReset() = softWrapParsingListeners.forEach { it.reset() }

  fun notifyRegionReparseStart(event: IncrementalCacheUpdateEvent) =
    softWrapParsingListeners.forEach { it.onRegionReparseStart(event) }

  fun notifyRegionReparseEnd(event: IncrementalCacheUpdateEvent) =
    softWrapParsingListeners.forEach { it.onRegionReparseEnd(event) }
  // endregion
}