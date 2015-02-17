package org.jetbrains.protocolReader

public class ClassScope(fileScope: FileScope, private val parentClass: ClassScope?) : FileScope(fileScope) {

  public fun getRootClassScope(): ClassScope {
    return if (parentClass == null) this else parentClass.getRootClassScope()
  }

  override fun asClassScope(): ClassScope? {
    return this
  }
}