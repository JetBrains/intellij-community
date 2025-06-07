// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import com.intellij.platform.syntax.impl.util.MutableBitSet
import com.intellij.platform.syntax.parser.WhitespacesAndCommentsBinder
import com.intellij.platform.syntax.parser.WhitespacesBinders
import com.intellij.util.fastutil.ints.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

internal class MarkerOptionalData {
  private val bitSet = MutableBitSet()

  private val myDebugAllocationPositions: MutableIntMap<Throwable> = Int2ObjectOpenHashMap()
  private val myDoneErrors: MutableIntMap<@Nls String> = Int2ObjectOpenHashMap()
  private val myLeftBinders: MutableIntMap<WhitespacesAndCommentsBinder> = Int2ObjectOpenHashMap()
  private val myRightBinders: MutableIntMap<WhitespacesAndCommentsBinder> = Int2ObjectOpenHashMap()
  private val myCollapsed: MutableIntSet = IntOpenHashSet()

  fun clean(markerId: Int) {
    if (bitSet.contains(markerId)) {
      bitSet.remove(markerId)
      myLeftBinders.remove(markerId)
      myRightBinders.remove(markerId)
      myDoneErrors.remove(markerId)
      myCollapsed.remove(markerId)
      myDebugAllocationPositions.remove(markerId)
    }
  }

  val collapsedMarkerSize
    get() = myCollapsed.size

  val collapsedMarkerIds: IntArray
    get() = myCollapsed.toIntArray()

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
    bitSet.add(markerId)
  }

  fun notifyAllocated(markerId: Int) {
    markAsHavingOptionalData(markerId)
    myDebugAllocationPositions.put(markerId, Throwable("Created at the following trace."))
  }

  fun getAllocationTrace(marker: ProductionMarker): Throwable? {
    return myDebugAllocationPositions[marker.markerId]
  }

  fun getBinder(markerId: Int, right: Boolean): WhitespacesAndCommentsBinder {
    val binder = if (bitSet.contains(markerId)) getBinderMap(right)[markerId] else null
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
}
