// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.PresentableChange

abstract class SelectionAwareGoToChangePopupActionProvider {
  abstract fun getActualProducers(): List<@JvmWildcard DiffRequestProducer>

  abstract fun select(change: PresentableChange)

  abstract fun getSelectedChange(): PresentableChange?

  fun createGoToChangeAction(): AnAction {
    return object : SimpleGoToChangePopupAction() {
      override fun getChanges(): ListSelection<out PresentableChange> {
        val producers = getActualProducers().map {
          it as? ChangeDiffRequestChain.Producer
          ?: throw IllegalArgumentException("Only " + ChangeDiffRequestChain.Producer::class.java + " are supported implementations")
        }
        val selectedChange = getSelectedChange()
        val selectedIndex = producers.indexOfFirst {
          it.filePath == selectedChange?.filePath &&
          it.fileStatus == selectedChange.fileStatus
          //it.tag == selectedChange.tag //TODO tag should be supported in all implementation
        }

        return ListSelection.createAt(producers, selectedIndex)
      }

      override fun onSelected(changes: List<PresentableChange>, selectedIndex: Int?) {
        if (selectedIndex != null) select(changes[selectedIndex])
      }
    }
  }
}
