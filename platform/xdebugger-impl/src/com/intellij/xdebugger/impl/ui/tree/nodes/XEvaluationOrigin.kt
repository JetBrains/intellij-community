// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes

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
  UNSPECIFIED_WATCH
}
