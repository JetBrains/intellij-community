package org.jetbrains.protocolReader

fun FileScope(globalScope: GlobalScope, stringBuilder: StringBuilder) = FileScope(TextOutput(stringBuilder), globalScope)

open class FileScope(public val output: TextOutput, globalScope: GlobalScope) : GlobalScope(globalScope.state) {
  fun newClassScope() = ClassScope(this, asClassScope())

  open protected fun asClassScope(): ClassScope? = null
}