package org.jetbrains.protocolReader

internal fun FileScope(globalScope: GlobalScope, stringBuilder: StringBuilder) = FileScope(TextOutput(stringBuilder), globalScope)

internal open class FileScope(val output: TextOutput, globalScope: GlobalScope) : GlobalScope(globalScope.state) {
  fun newClassScope() = ClassScope(this, asClassScope())

  open protected fun asClassScope(): ClassScope? = null
}