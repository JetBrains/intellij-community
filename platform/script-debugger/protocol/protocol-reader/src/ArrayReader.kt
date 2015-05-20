package org.jetbrains.protocolReader

class ArrayReader(private val componentParser: ValueReader, private val isList: Boolean) : ValueReader() {
  override public fun appendFinishedValueTypeName(out: TextOutput) {
    if (isList) {
      out.append("List<")
      componentParser.appendFinishedValueTypeName(out)
      out.append('>')
    }
    else {
      componentParser.appendFinishedValueTypeName(out)
      out.append("[]")
    }
  }

  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    componentParser.writeArrayReadCode(scope, subtyping, out)
  }
}
