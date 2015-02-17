package org.jetbrains.protocolReader


fun FileScope(globalScope: GlobalScope, stringBuilder: StringBuilder): FileScope {
  val __ = FileScope(TextOutput(stringBuilder))
  `super`(globalScope)
  return __
}

fun FileScope(fileScope: FileScope): FileScope {
  val __ = FileScope(fileScope.output)
  `super`(fileScope)
  return __
}

class FileScope(public val output: TextOutput) : GlobalScope() {

  public fun newClassScope(): ClassScope {
    return ClassScope(this, asClassScope())
  }

  protected fun asClassScope(): ClassScope? {
    return null
  }
}