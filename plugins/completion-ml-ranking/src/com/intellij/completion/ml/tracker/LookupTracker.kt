// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.tracker

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.util.language
import com.intellij.completion.ml.storage.MutableLookupStorage

abstract class LookupTracker : LookupManagerListener {
  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    if (newLookup is LookupImpl) {
      val language = newLookup.language()
      if (language != null) {
        val lookupStorage = MutableLookupStorage.initOrGetLookupStorage(newLookup, language)
        lookupCreated(newLookup, lookupStorage)
      }
    }
    else {
      lookupClosed()
    }
  }

  protected abstract fun lookupCreated(lookup: LookupImpl, storage: MutableLookupStorage)

  protected open fun lookupClosed() {}
}
