// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.vcsUtil.VcsUtil
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import java.util.*

abstract class BaseInclusionModel : InclusionModel {
  private val inclusionEventDispatcher = EventDispatcher.create(InclusionListener::class.java)

  override fun addInclusionListener(listener: InclusionListener) = inclusionEventDispatcher.addListener(listener)
  override fun removeInclusionListener(listener: InclusionListener) = inclusionEventDispatcher.removeListener(listener)

  protected fun fireInclusionChanged() = inclusionEventDispatcher.multicaster.inclusionChanged()
}

object NullInclusionModel : InclusionModel {
  override fun getInclusion(): Set<Any> = emptySet()
  override fun getInclusionState(item: Any): ThreeStateCheckBox.State = ThreeStateCheckBox.State.NOT_SELECTED
  override fun isInclusionEmpty(): Boolean = true

  override fun addInclusion(items: Collection<Any>) = Unit
  override fun removeInclusion(items: Collection<Any>) = Unit
  override fun setInclusion(items: Collection<Any>) = Unit
  override fun retainInclusion(items: Collection<Any>) = Unit
  override fun clearInclusion() = Unit

  override fun addInclusionListener(listener: InclusionListener) = Unit
  override fun removeInclusionListener(listener: InclusionListener) = Unit
}

class DefaultInclusionModel(
  private val inclusionHashingStrategy: Hash.Strategy<Any>? = null
) : BaseInclusionModel() {
  private val inclusion: MutableSet<Any> = if (inclusionHashingStrategy == null) HashSet() else ObjectOpenCustomHashSet(inclusionHashingStrategy)

  override fun getInclusion(): Set<Any> = Collections.unmodifiableSet<Any>((if (inclusionHashingStrategy == null) HashSet(inclusion) else ObjectOpenCustomHashSet(inclusion, inclusionHashingStrategy)))

  override fun getInclusionState(item: Any): ThreeStateCheckBox.State =
    if (item in inclusion) ThreeStateCheckBox.State.SELECTED else ThreeStateCheckBox.State.NOT_SELECTED

  override fun isInclusionEmpty(): Boolean = inclusion.isEmpty()

  override fun addInclusion(items: Collection<Any>) {
    if (inclusion.addAll(items)) fireInclusionChanged()
  }

  override fun removeInclusion(items: Collection<Any>) {
    if (VcsUtil.removeAllFromSet(inclusion, items)) fireInclusionChanged()
  }

  override fun setInclusion(items: Collection<Any>) {
    val oldInclusion = getInclusion()
    inclusion.clear()
    inclusion.addAll(items)

    if (oldInclusion != inclusion) fireInclusionChanged()
  }

  override fun retainInclusion(items: Collection<Any>) {
    if (inclusion.retainAll(items)) fireInclusionChanged()
  }

  override fun clearInclusion() {
    if (inclusion.isNotEmpty()) {
      inclusion.clear()
      fireInclusionChanged()
    }
  }
}