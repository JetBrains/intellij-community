package org.jetbrains.protocolReader

internal class MapReader(private val componentParser: ValueReader) : ValueReader() {
  override fun appendFinishedValueTypeName(out: TextOutput) {
    out.append("Map")
    out.append('<')
    out.append("String, ")
    componentParser.appendFinishedValueTypeName(out)
    out.append('>')
  }

  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    beginReadCall("Map", subtyping, out)
    if (componentParser is ObjectValueReader) {
      componentParser.writeFactoryArgument(scope, out)
    }
    else {
      out.comma().append("null")
    }
    out.append(')')
  }

  override fun writeArrayReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    beginReadCall("ObjectArray", subtyping, out)
    out.comma().append("mapFactory(")
    if (componentParser is ObjectValueReader) {
      componentParser.writeFactoryNewExpression(scope, out)
    }
    else {
      out.append("STRING_OBJECT_FACTORY")
    }
    out.append("))")
  }
}
