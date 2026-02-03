package org.jetbrains.protocolReader

internal fun FileScope(globalScope: GlobalScope, stringBuilder: StringBuilder) = FileScope(TextOutput(stringBuilder), globalScope)

internal open class FileScope(val output: TextOutput, globalScope: GlobalScope) : GlobalScope(globalScope.state) {
  fun newClassScope() = ClassScope(this, asClassScope())

  protected open fun asClassScope(): ClassScope? = null
}