package org.jetbrains.protocolReader

internal class ObjectValueReader(val type: TypeRef<*>, private val isSubtyping: Boolean, primitiveValueName: String?) : ValueReader() {
  val primitiveValueName = if (primitiveValueName == null || primitiveValueName.isEmpty()) null else primitiveValueName

  override fun asJsonTypeParser() = this

  fun isSubtyping() = isSubtyping

  override fun appendFinishedValueTypeName(out: TextOutput) {
    out.append(type.typeClass.canonicalName)
  }

  override fun appendInternalValueTypeName(scope: FileScope, out: TextOutput) {
    out.append(scope.getTypeImplReference(type.type!!))
  }

  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    type.type!!.writeInstantiateCode(scope.getRootClassScope(), subtyping, out)
    out.append('(')
    addReaderParameter(subtyping, out)
    out.comma().append("null")
    if (subtyping && type.type!!.subtypeAspect != null) {
      out.comma().append("this")
    }
    out.append(')')
  }

  override fun writeArrayReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    beginReadCall("ObjectArray", subtyping, out)
    writeFactoryArgument(scope, out)
    out.append(')')
  }

  fun writeFactoryArgument(scope: ClassScope, out: TextOutput) {
    out.comma()
    writeFactoryNewExpression(scope, out)
  }

  fun writeFactoryNewExpression(scope: ClassScope, out: TextOutput) {
    out.append(TYPE_FACTORY_NAME_PREFIX).append(scope.requireFactoryGenerationAndGetName(type.type!!)).append("()")
  }
}
