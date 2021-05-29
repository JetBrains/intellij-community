// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain

abstract class SelectionAwareGoToChangePopupActionProvider {
  abstract fun getActualProducers(): List<@JvmWildcard DiffRequestProducer>

  abstract fun selectFilePath(filePath: FilePath)

  abstract fun getSelectedFilePath(): FilePath?

  fun createGoToChangeAction(): AnAction {
    val producers = getActualProducers().map {
      it as? ChangeDiffRequestChain.Producer
      ?: throw IllegalArgumentException("Only " + ChangeDiffRequestChain.Producer::class.java + " are supported implementations")
    }
    val selectedFilePath = getSelectedFilePath()
    val selectedIndex = producers.indexOfFirst { it.filePath == selectedFilePath }

    return object : SimpleGoToChangePopupAction(producers, selectedIndex) {
      override fun onSelected(selectedIndex: Int?) {
        if (selectedIndex != null) selectFilePath(producers[selectedIndex].filePath)
      }
    }
  }
}
