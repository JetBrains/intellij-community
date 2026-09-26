package com.intellij.execution.process

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PtyProcessControl {
  val enterKeyCode: Byte? get() = null

  val isConsoleMode: Boolean get() = false

  val isConPty: Boolean get() = false

  val isConPtyInheritCursor: Boolean get() = false

  fun setWindowSize(columns: Int, rows: Int)
}
