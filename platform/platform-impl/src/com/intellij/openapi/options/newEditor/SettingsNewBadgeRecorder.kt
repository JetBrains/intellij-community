// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@Service(Service.Level.APP)
internal class SettingsNewBadgeRecorder(coroutineScope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(): SettingsNewBadgeRecorder = service()

    const val KEY_PREFIX: String = "settings.new.badge.shown.count."
    const val MAX_SHOWS: Int = 3

    private val DEBOUNCE = 500.milliseconds
  }

  private val currentTreeView = MutableStateFlow<SettingsTreeView?>(null)
  private val requests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      currentTreeView.combine(requests) { treeView, _ -> treeView }
        .debounce(DEBOUNCE)
        .collectLatest { treeView ->
          if (treeView == null) return@collectLatest
          runCatching {
            record(treeView)
          }.getOrHandleException { e ->
            LOG.error("Failed to record visible Settings 'New' badges", e)
          }
        }
    }
  }

  fun request(treeView: SettingsTreeView) {
    currentTreeView.value = treeView
    check(requests.tryEmit(Unit))
  }

  fun release(treeView: SettingsTreeView) {
    currentTreeView.compareAndSet(treeView, null)
  }

  fun shownCount(configurable: Configurable): Int {
    return PropertiesComponent.getInstance().getInt(KEY_PREFIX + ConfigurableVisitor.getId(configurable), 0)
  }

  @RequiresEdt
  private fun record(treeView: SettingsTreeView) {
    val tree = treeView.tree
    val viewRect = tree.visibleRect
    if (viewRect.isEmpty) return

    val props = PropertiesComponent.getInstance()
    for (row in 0 until tree.rowCount) {
      val bounds = tree.getRowBounds(row) ?: continue
      if (!bounds.intersects(viewRect)) continue

      val path = tree.getPathForRow(row) ?: continue
      val last = path.lastPathComponent
      val configurable = treeView.configurableWithNewBadgeAt(last) ?: continue

      val leaf = tree.model.isLeaf(last)
      val expanded = tree.isExpanded(path)
      if (!leaf && expanded) continue

      val id = ConfigurableVisitor.getId(configurable)
      val key = KEY_PREFIX + id
      val shown = props.getInt(key, 0)
      treeView.captureNewBadgeSnapshot(id, shown)
      if (!treeView.markNewBadgeRecordedThisOpen(id)) continue
      if (shown < MAX_SHOWS) {
        props.setValue(key, shown + 1, 0)
      }
    }
  }
}

private val LOG = logger<SettingsNewBadgeRecorder>()
