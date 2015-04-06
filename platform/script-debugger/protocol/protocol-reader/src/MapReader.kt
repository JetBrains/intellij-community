package org.jetbrains.protocolReader

class MapReader(componentParser: ValueReader?) : ValueReader() {
  private val componentParser: ValueReader?

  init {
    if (componentParser == null || componentParser is ObjectValueReader) {
      this.componentParser = componentParser
    }
    else {
      // if primitive (String), we don't need to use factory to read value
      this.componentParser = null
    }
  }

  override fun appendFinishedValueTypeName(out: TextOutput) {
    out.append("java.util.Map")
    if (componentParser != null) {
      out.append('<')
      out.append("String, ")
      componentParser.appendFinishedValueTypeName(out)
      out.append('>')
    }
  }

  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    beginReadCall("Map", subtyping, out)
    if (componentParser == null) {
      out.comma().append("null")
    }
    else {
      (componentParser as ObjectValueReader).writeFactoryArgument(scope, out)
    }
    out.append(')')
  }

  override fun writeArrayReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    beginReadCall("ObjectArray", subtyping, out)
    out.comma().append("new org.jetbrains.jsonProtocol.MapFactory(")
    assert(componentParser != null)
    (componentParser as ObjectValueReader).writeFactoryNewExpression(scope, out)
    out.append("))")
  }
}
