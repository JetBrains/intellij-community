package org.jetbrains.protocolReader

class ObjectValueReader(val type: TypeRef<*>, private val isSubtyping: Boolean, primitiveValueName: String?) : ValueReader() {
  val primitiveValueName: String?

  init {
    this.primitiveValueName = if (primitiveValueName == null || primitiveValueName.isEmpty()) null else primitiveValueName
  }

  override public fun asJsonTypeParser(): ObjectValueReader {
    return this
  }

  public fun isSubtyping(): Boolean {
    return isSubtyping
  }

  override public fun appendFinishedValueTypeName(out: TextOutput) {
    out.append(type.typeClass.getCanonicalName())
  }

  override public fun appendInternalValueTypeName(scope: FileScope, out: TextOutput) {
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

  override public fun writeArrayReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    beginReadCall("ObjectArray", subtyping, out)
    writeFactoryArgument(scope, out)
    out.append(')')
  }

  fun writeFactoryArgument(scope: ClassScope, out: TextOutput) {
    out.comma()
    writeFactoryNewExpression(scope, out)
  }

  fun writeFactoryNewExpression(scope: ClassScope, out: TextOutput) {
    out.append("new ").append(scope.requireFactoryGenerationAndGetName(type.type!!)).append(TYPE_FACTORY_NAME_POSTFIX).append("()")
  }
}
