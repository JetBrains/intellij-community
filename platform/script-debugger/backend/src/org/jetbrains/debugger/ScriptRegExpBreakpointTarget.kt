package org.jetbrains.debugger

class ScriptRegExpBreakpointTarget(private val regExp: String, val language: String? = null) : BreakpointTarget() {
  override fun <R> accept(visitor: BreakpointTarget.Visitor<R>): R {
    if (visitor is ScriptRegExpSupportVisitor<*>) {
      return (visitor as ScriptRegExpSupportVisitor<R>).visitRegExp(this)
    }
    else {
      return visitor.visitUnknown(this)
    }
  }

  override fun toString() = regExp

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other == null || javaClass != other.javaClass) {
      return false
    }
    return regExp == (other as ScriptRegExpBreakpointTarget).regExp
  }

  override fun hashCode() = regExp.hashCode()
}