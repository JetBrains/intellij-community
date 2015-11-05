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

  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    componentParser.writeArrayReadCode(scope, subtyping, out)
  }
}
