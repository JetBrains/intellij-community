package org.jetbrains.protocolReader

internal class ClassScope(fileScope: FileScope, private val parentClass: ClassScope?) : FileScope(fileScope.output, fileScope) {
  fun getRootClassScope(): ClassScope = if (parentClass == null) this else parentClass.getRootClassScope()

  override fun asClassScope() = this
}