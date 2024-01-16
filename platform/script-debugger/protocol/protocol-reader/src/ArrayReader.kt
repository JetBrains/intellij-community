package org.jetbrains.protocolReader

internal class ArrayReader(private val componentParser: ValueReader, private val isList: Boolean) : ValueReader() {
  override fun appendFinishedValueTypeName(out: TextOutput) {
    if (isList) {
      out.append("List<")
    }
    else {
      if (componentParser is PrimitiveValueReader && (componentParser.className == "Int" || componentParser.className == "Double" || componentParser.className == "Float")) {
        out.append(componentParser.className).append("Array")
        return
      }

      out.append("Array<")
    }
    componentParser.appendFinishedValueTypeName(out)
    out.append('>')
  }

  override fun writeArrayReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    beginReadCall("ObjectArray", subtyping, out)
    out
      .comma()
      // the trick with shadowing isn't very good, but at least it's simple
      .append("WrapperFactory { $READER_NAME -> ")
    componentParser.writeArrayReadCode(scope, subtyping, out)
    out.append("}")
    out.append(')')
  }

  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    componentParser.writeArrayReadCode(scope, subtyping, out)
  }
}
