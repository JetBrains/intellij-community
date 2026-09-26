// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CompositeModificationTracker(modificationTrackers: Sequence<ModificationTracker>) : ModificationTracker {

  val modificationTrackers: Set<ModificationTracker> = modificationTrackers
    .flatMapTo(mutableSetOf()) { if (it is CompositeModificationTracker) it.modificationTrackers else setOf(it) }
    .let {
      if (it.contains(ModificationTracker.EVER_CHANGED))
        setOf(ModificationTracker.EVER_CHANGED)
      else if (it.isEmpty())
        throw IllegalArgumentException("At least one modification tracker should be provided to the CompositeModificationTracker")
      else if (it.size == 1)
        it.toSet()
      else
        it.also { it.remove(ModificationTracker.NEVER_CHANGED) }.toSet()
    }

  constructor(modificationTracker: ModificationTracker, vararg modificationTrackers: ModificationTracker) : this(
    sequenceOf(modificationTracker).plus(modificationTrackers)
  )

  override fun getModificationCount(): Long =
    modificationTrackers.sumOf { it.modificationCount }

}
