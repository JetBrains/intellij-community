// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.openapi.util.NlsContexts
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet

internal class CompositeOptionalData {
  private val errors: Int2ObjectMap<@NlsContexts.DetailedDescription String> = Int2ObjectOpenHashMap<String>()
  private val collapsed: IntSet = IntOpenHashSet()

  fun getErrorMessage(markerId: Int): @NlsContexts.DetailedDescription String? =
    errors.get(markerId)

  fun setErrorMessage(markerId: Int, message: @NlsContexts.DetailedDescription String) {
    errors.put(markerId, message)
  }

  fun isCollapsed(markerId: Int): Boolean =
    collapsed.contains(markerId)

  fun markCollapsed(markerId: Int) {
    collapsed.add(markerId)
  }
}