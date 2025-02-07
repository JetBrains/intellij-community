// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import com.intellij.platform.syntax.impl.fastutil.ints.Int2ObjectOpenHashMap
import com.intellij.platform.syntax.impl.fastutil.ints.IntOpenHashSet
import com.intellij.platform.syntax.impl.fastutil.ints.MutableIntMap
import com.intellij.platform.syntax.impl.fastutil.ints.MutableIntSet
import com.intellij.platform.syntax.parser.WhitespacesAndCommentsBinder
import com.intellij.platform.syntax.parser.WhitespacesBinders
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

internal class MarkerOptionalData {
  private var bitset = LongArray(16) { 0 }

  private val myDebugAllocationPositions: MutableIntMap<Throwable> = Int2ObjectOpenHashMap()
  private val myDoneErrors: MutableIntMap<@Nls String> = Int2ObjectOpenHashMap()
  private val myLeftBinders: MutableIntMap<WhitespacesAndCommentsBinder> = Int2ObjectOpenHashMap()
  private val myRightBinders: MutableIntMap<WhitespacesAndCommentsBinder> = Int2ObjectOpenHashMap()
  private val myCollapsed: MutableIntSet = IntOpenHashSet()

  fun clean(markerId: Int) {
    if (contains(markerId)) {
      remove(markerId)
      myLeftBinders.remove(markerId)
      myRightBinders.remove(markerId)
      myDoneErrors.remove(markerId)
      myCollapsed.remove(markerId)
      myDebugAllocationPositions.remove(markerId)
    }
  }

  @ApiStatus.Internal
  fun getDoneError(markerId: Int): @Nls String? = myDoneErrors[markerId]

  fun isCollapsed(markerId: Int): Boolean = markerId in myCollapsed

  fun setErrorMessage(markerId: Int, message: @Nls String) {
    markAsHavingOptionalData(markerId)
    myDoneErrors.put(markerId, message)
  }

  fun markCollapsed(markerId: Int) {
    markAsHavingOptionalData(markerId)
    myCollapsed.add(markerId)
  }

  private fun markAsHavingOptionalData(markerId: Int) {
    add(markerId)
  }

  fun notifyAllocated(markerId: Int) {
    markAsHavingOptionalData(markerId)
    myDebugAllocationPositions.put(markerId, Throwable("Created at the following trace."))
  }

  fun getAllocationTrace(marker: ProductionMarker): Throwable? {
    return myDebugAllocationPositions[marker.markerId]
  }

  fun getBinder(markerId: Int, right: Boolean): WhitespacesAndCommentsBinder {
    val binder = if (contains(markerId)) getBinderMap(right)[markerId] else null
    return binder ?: getDefaultBinder(right)
  }

  fun assignBinder(markerId: Int, binder: WhitespacesAndCommentsBinder, right: Boolean) {
    val map = getBinderMap(right)
    if (binder !== getDefaultBinder(right)) {
      markAsHavingOptionalData(markerId)
      map.put(markerId, binder)
    }
    else {
      map.remove(markerId)
    }
  }

  private fun getBinderMap(right: Boolean): MutableIntMap<WhitespacesAndCommentsBinder> {
    return if (right) myRightBinders else myLeftBinders
  }

  private fun getDefaultBinder(right: Boolean): WhitespacesAndCommentsBinder {
    return if (right) WhitespacesBinders.defaultRightBinder() else WhitespacesBinders.defaultLeftBinder()
  }

  internal fun add(markerId: Int) {
    ensureCapacity(markerId)
    val index = markerId shr indexShift
    bitset[index] = bitset[index] or (1L shl markerId)
  }

  internal fun contains(markerId: Int): Boolean {
    val index = markerId shr indexShift
    if (index >= bitset.size) return false
    return bitset[index] and (1L shl markerId) != 0L
  }

  internal fun remove(markerId: Int) {
    val index = markerId shr indexShift
    bitset[index] = bitset[index] and (1L shl markerId).inv()
  }

  private fun ensureCapacity(markerId: Int) {
    val index = markerId shr indexShift
    if (index >= bitset.size) {
      bitset = bitset.copyOf(bitset.size * 3 / 2)
    }
  }
}

private const val indexShift = 6