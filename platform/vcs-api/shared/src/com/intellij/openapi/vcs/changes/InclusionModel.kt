// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.util.ui.ThreeStateCheckBox
import java.util.*

interface InclusionModel {
  fun getInclusion(): Set<Any>
  fun getInclusionState(item: Any): ThreeStateCheckBox.State
  fun isInclusionEmpty(): Boolean

  /**
   * Modifications are allowed from EDT only
   */
  fun addInclusion(items: Collection<Any>)
  fun removeInclusion(items: Collection<Any>)
  fun setInclusion(items: Collection<Any>)
  fun retainInclusion(items: Collection<Any>)
  fun clearInclusion()

  fun addInclusionListener(listener: InclusionListener)
  fun removeInclusionListener(listener: InclusionListener)
}

interface InclusionListener : EventListener {
  fun inclusionChanged()
}