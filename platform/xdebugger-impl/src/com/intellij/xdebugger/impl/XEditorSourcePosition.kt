// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.editor.Editor
import com.intellij.xdebugger.XSourcePosition
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XEditorSourcePosition : XSourcePosition {
  val editor: Editor
}

@ApiStatus.Internal
fun XSourcePosition.withEditor(editor: Editor): XEditorSourcePosition {
  return XEditorSourcePositionImpl(editor, this)
}

private class XEditorSourcePositionImpl(override val editor: Editor, position: XSourcePosition) : XEditorSourcePosition, XSourcePosition by position