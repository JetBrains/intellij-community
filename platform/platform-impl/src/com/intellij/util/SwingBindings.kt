// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.ui.DocumentAdapter
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Experimental
import javax.swing.JTabbedPane
import javax.swing.text.JTextComponent

@Experimental
fun JTextComponent.bindTextOnShow(textState: MutableStateFlow<String>, launchDebugName: String) {
  launchOnShow(launchDebugName) {
    bindTextIn(textState, this)
  }
}

@Suppress("DuplicatedCode")
@Experimental
fun JTextComponent.bindTextIn(textState: MutableStateFlow<String>, coroutineScope: CoroutineScope) {
  val listener = object : DocumentAdapter() {
    override fun textChanged(e: javax.swing.event.DocumentEvent) {
      textState.update { text }
    }
  }
  coroutineScope.launch {
    textState.collectLatest { textValue ->
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        document.removeDocumentListener(listener)
        text = textValue
        document.addDocumentListener(listener)
      }
    }
  }.invokeOnCompletion {
    document.removeDocumentListener(listener)
  }
}

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