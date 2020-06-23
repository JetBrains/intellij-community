// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

class ScriptRegExpBreakpointTarget(private val regExp: String) : BreakpointTarget() {
  override fun <R> accept(visitor: Visitor<R>): R {
    if (visitor is ScriptRegExpSupportVisitor<*>) {
      return (visitor as ScriptRegExpSupportVisitor<R>).visitRegExp(this)
    }
    else {
      return visitor.visitUnknown(this)
    }
  }

  override fun toString(): String = regExp

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other == null || javaClass != other.javaClass) {
      return false
    }
    return regExp == (other as ScriptRegExpBreakpointTarget).regExp
  }

  override fun hashCode(): Int = regExp.hashCode()
}