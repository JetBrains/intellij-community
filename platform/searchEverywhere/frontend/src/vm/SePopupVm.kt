// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.SeTab
import fleet.kernel.DurableRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class SePopupVm(val coroutineScope: CoroutineScope,
                private val project: Project,
                private val sessionRef: DurableRef<SeSessionEntity>,
                tabs: List<SeTab>,
                private val onClose: suspend () -> Unit) {

  val currentTabIndex: MutableStateFlow<Int> = MutableStateFlow(0)

  val currentTab: Flow<SeTabVm>
  val searchResults: Flow<Flow<SeItemData>>

  val searchPattern = MutableStateFlow("")

  val tabVms: List<SeTabVm> = tabs.map {
    SeTabVm(coroutineScope, it, searchPattern)
  }

  init {
    check(tabVms.isNotEmpty()) { "Search Everywhere tabs must not be empty" }

    val activeTab = tabVms.first()
    currentTab = currentTabIndex.map {
      tabVms[it.coerceIn(tabVms.indices)]
    }.withPrevious().map { (prev, next) ->
      prev?.setActive(false)
      next.setActive(true)
      next
    }
    searchResults = currentTab.flatMapLatest { it.searchResults }
    activeTab.setActive(true)
  }

  fun dispose() {
    coroutineScope.launch {
      onClose()
    }
  }
}

private fun <T> Flow<T>.withPrevious(): Flow<Pair<T?, T>> = flow {
  var previous: T? = null
  collect { current ->
    emit(Pair(previous, current))
    previous = current
  }
}
