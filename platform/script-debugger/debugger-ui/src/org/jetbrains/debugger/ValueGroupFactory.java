package org.jetbrains.debugger

import com.intellij.xdebugger.frame.XValueGroup

internal interface ValueGroupFactory<T> {
  fun create(data: T, start: Int, end: Int, context: VariableContext): XValueGroup
}