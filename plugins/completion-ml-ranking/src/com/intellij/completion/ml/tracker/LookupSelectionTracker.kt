// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.tracker

import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.completion.ml.util.idString
import com.intellij.completion.ml.personalization.session.ElementSessionFactorsStorage
import com.intellij.completion.ml.storage.LookupStorage

class LookupSelectionTracker(private val storage: LookupStorage) : LookupListener {
  private var currentElementStorage: ElementSessionFactorsStorage? = null
  override fun lookupShown(event: LookupEvent) = selectionChanged(event)
  override fun currentItemChanged(event: LookupEvent) = selectionChanged(event)

  private fun selectionChanged(event: LookupEvent) {
    val lookupElement = event.item
    if (lookupElement != null) {
      val elementStorage = storage.getItemStorage(lookupElement.idString()).sessionFactors

      if (elementStorage == currentElementStorage) return

      elementStorage.selectionTracker.itemSelected()
      currentElementStorage?.selectionTracker?.itemUnselected()
      currentElementStorage = elementStorage
    }
  }
}