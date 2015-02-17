package org.jetbrains.protocolReader

class ClassScope(fileScope: FileScope, private val parentClass: ClassScope?) : FileScope(fileScope.output, fileScope) {
  fun getRootClassScope(): ClassScope = if (parentClass == null) this else parentClass.getRootClassScope()

  override fun asClassScope() = this
}