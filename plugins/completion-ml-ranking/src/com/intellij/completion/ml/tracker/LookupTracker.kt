// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.tracker

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.util.language
import com.intellij.completion.ml.storage.MutableLookupStorage
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

abstract class LookupTracker : PropertyChangeListener {
  override fun propertyChange(evt: PropertyChangeEvent?) {
    val lookup = evt?.newValue
    if (lookup is LookupImpl) {
      val language = lookup.language()
      if (language != null) {
        val lookupStorage = MutableLookupStorage.initOrGetLookupStorage(lookup, language)
        lookupCreated(lookup, lookupStorage)
      }
    }
    else {
      lookupClosed()
    }
  }

  protected abstract fun lookupCreated(lookup: LookupImpl, storage: MutableLookupStorage)

  protected open fun lookupClosed() {}
}
