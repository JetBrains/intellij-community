// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ThrowableComputable
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
    fun getOrigin(holder: UserDataHolderBase): XEvaluationOrigin {
      return ORIGIN_KEY.get(holder, UNSPECIFIED)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun setOrigin(holder: UserDataHolderBase, origin: XEvaluationOrigin) {
      ORIGIN_KEY.set(holder, origin.takeIf { it != UNSPECIFIED })
    }

    @ApiStatus.Internal
    @JvmStatic
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
  }
}
