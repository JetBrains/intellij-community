package org.jetbrains.protocolReader

class ObjectValueReader<T>(val type: TypeRef<T>, private val isSubtyping: Boolean, primitiveValueName: String?) : ValueReader() {
  val primitiveValueName: String?

  {
    this.primitiveValueName = if (primitiveValueName == null || primitiveValueName.isEmpty()) null else primitiveValueName
  }

  public fun asJsonTypeParser(): ObjectValueReader<Any> {
    return this
  }

  public fun isSubtyping(): Boolean {
    return isSubtyping
  }

  public fun appendFinishedValueTypeName(out: TextOutput) {
    out.append(type.typeClass.getCanonicalName())
  }

  public fun appendInternalValueTypeName(classScope: FileScope, out: TextOutput) {
    out.append(classScope.getTypeImplReference(type.type))
  }

  fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    type.type.writeInstantiateCode(scope.getRootClassScope(), subtyping, out)
    out.append('(')
    addReaderParameter(subtyping, out)
    out.comma().append("null")
    if (subtyping && type.type.subtypeAspect != null) {
      out.comma().append("this")
    }
    out.append(')')
  }

  public fun writeArrayReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    beginReadCall("ObjectArray", subtyping, out)
    writeFactoryArgument(scope, out)
    out.append(')')
  }

  fun writeFactoryArgument(scope: ClassScope, out: TextOutput) {
    out.comma()
    writeFactoryNewExpression(scope, out)
  }

  fun writeFactoryNewExpression(scope: ClassScope, out: TextOutput) {
    out.append("new ").append(scope.requireFactoryGenerationAndGetName(type.type)).append(Util.TYPE_FACTORY_NAME_POSTFIX).append("()")
  }
}
