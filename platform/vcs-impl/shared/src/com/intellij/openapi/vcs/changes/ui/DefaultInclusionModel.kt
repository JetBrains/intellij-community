// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

abstract class BaseInclusionModel : InclusionModel {
  private val inclusionEventDispatcher = EventDispatcher.create(InclusionListener::class.java)

  override fun addInclusionListener(listener: InclusionListener) = inclusionEventDispatcher.addListener(listener)
  override fun removeInclusionListener(listener: InclusionListener) = inclusionEventDispatcher.removeListener(listener)

  protected fun fireInclusionChanged() = inclusionEventDispatcher.multicaster.inclusionChanged()
}

@ApiStatus.Internal
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
  private val inclusionHashingStrategy: HashingStrategy<Any>? = null
) : BaseInclusionModel() {
  private val lock = ReentrantReadWriteLock()
  private val inclusion: MutableSet<Any> = if (inclusionHashingStrategy == null) HashSet() else CollectionFactory.createCustomHashingStrategySet(inclusionHashingStrategy)

  override fun getInclusion(): Set<Any> {
    lock.read {
      val set = if (inclusionHashingStrategy == null) HashSet(inclusion)
      else CollectionFactory.createCustomHashingStrategySet(inclusionHashingStrategy).also { it.addAll(inclusion) }
      return Collections.unmodifiableSet(set)
    }
  }

  override fun getInclusionState(item: Any): ThreeStateCheckBox.State {
    lock.read {
      val isIncluded = item in inclusion
      return if (isIncluded) ThreeStateCheckBox.State.SELECTED else ThreeStateCheckBox.State.NOT_SELECTED
    }
  }

  override fun isInclusionEmpty(): Boolean {
    lock.read {
      return inclusion.isEmpty()
    }
  }

  override fun addInclusion(items: Collection<Any>) {
    val wasChanged = lock.write {
      inclusion.addAll(items)
    }
    if (wasChanged) fireInclusionChanged()
  }

  override fun removeInclusion(items: Collection<Any>) {
    val wasChanged = lock.write {
      items.fold(false) { acc, element -> acc or inclusion.remove(element) }
    }
    if (wasChanged) fireInclusionChanged()
  }

  override fun setInclusion(items: Collection<Any>) {
    val wasChanged: Boolean
    lock.write {
      val oldInclusion = getInclusion()
      inclusion.clear()
      inclusion.addAll(items)
      wasChanged = oldInclusion != inclusion
    }
    if (wasChanged) fireInclusionChanged()
  }

  override fun retainInclusion(items: Collection<Any>) {
    val wasChanged = lock.write {
      inclusion.retainAll(items)
    }
    if (wasChanged) fireInclusionChanged()
  }

  override fun clearInclusion() {
    val wasChanged: Boolean
    lock.write {
      if (inclusion.isNotEmpty()) {
        inclusion.clear()
        wasChanged = true
      }
      else {
        wasChanged = false
      }
    }
    if (wasChanged) fireInclusionChanged()
  }
}