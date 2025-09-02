// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Experimental
import javax.swing.JTabbedPane


@Experimental
fun JTabbedPane.bindSelectedTabIn(selectedTabState: MutableStateFlow<Int>, coroutineScope: CoroutineScope) {
  val changeListener = javax.swing.event.ChangeListener {
    val selectedIndex = selectedIndex
    if (selectedTabState.value != selectedIndex) {
      selectedTabState.update { selectedIndex }
    }
  }

  addChangeListener(changeListener)

  coroutineScope.launch {
    selectedTabState.collectLatest { tabIndex ->
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        if (selectedIndex != tabIndex) {
          removeChangeListener(changeListener)
          selectedIndex = tabIndex
          addChangeListener(changeListener)
        }
      }
    }
  }.invokeOnCompletion {
    removeChangeListener(changeListener)
  }
}