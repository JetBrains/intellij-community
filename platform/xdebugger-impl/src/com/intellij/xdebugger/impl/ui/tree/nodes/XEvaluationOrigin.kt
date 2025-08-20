// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.annotations.ApiStatus

enum class XEvaluationOrigin {
  INLINE,
  DIALOG,
  WATCH,
  INLINE_WATCH,
  BREAKPOINT_CONDITION,
  BREAKPOINT_LOG,
  RENDERER,
  EDITOR,               // both on hover and on quick evaluate
  UNSPECIFIED,
  UNSPECIFIED_WATCH;

  @ApiStatus.Internal
  companion object {
    @JvmStatic
    private val ORIGIN_KEY: Key<XEvaluationOrigin> = Key<XEvaluationOrigin>("XEvaluationOrigin")

    @ApiStatus.Internal
    @JvmStatic
    @Deprecated("Use the non deprecated overload", level = DeprecationLevel.HIDDEN)
    fun getOrigin(holder: UserDataHolderBase): XEvaluationOrigin {
      return getOrigin(holder)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun getOrigin(holder: UserDataHolder): XEvaluationOrigin {
      return ORIGIN_KEY.get(holder, UNSPECIFIED)
    }

    @ApiStatus.Internal
    @JvmStatic
    @Deprecated("Use the non deprecated overload", level = DeprecationLevel.HIDDEN)
    fun setOrigin(holder: UserDataHolderBase, origin: XEvaluationOrigin) {
      setOrigin(holder, origin)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun setOrigin(holder: UserDataHolder, origin: XEvaluationOrigin) {
      ORIGIN_KEY.set(holder, origin.takeIf { it != UNSPECIFIED })
    }

    @ApiStatus.Internal
    @JvmStatic
    @Deprecated("Use the non deprecated overload", level = DeprecationLevel.HIDDEN)
    fun <T> computeWithOrigin(holder: UserDataHolderBase, origin: XEvaluationOrigin, block: ThrowableComputable<T, *>): T {
      val previous = holder.getUserData(ORIGIN_KEY)
      try {
        holder.putUserData(ORIGIN_KEY, origin)
        return block.compute()
      }
      finally {
        holder.putUserData(ORIGIN_KEY, previous)
      }
    }

    @ApiStatus.Internal
    @JvmStatic
    fun <T> computeWithOrigin(holder: UserDataHolder, origin: XEvaluationOrigin, block: ThrowableComputable<T, *>): T {
      val previous = holder.getUserData(ORIGIN_KEY)
      try {
        holder.putUserData(ORIGIN_KEY, origin)
        return block.compute()
      }
      finally {
        holder.putUserData(ORIGIN_KEY, previous)
      }
    }
  }
}
