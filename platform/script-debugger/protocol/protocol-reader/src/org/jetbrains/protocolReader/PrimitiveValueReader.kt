package org.jetbrains.protocolReader


fun PrimitiveValueReader(name: String): PrimitiveValueReader {
  return PrimitiveValueReader(name, null, false)
}

fun PrimitiveValueReader(name: String, defaultValue: String): PrimitiveValueReader {
  return PrimitiveValueReader(name, defaultValue, false)
}

class PrimitiveValueReader(private val className: String, val defaultValue: String?, private val asRawString: Boolean) : ValueReader() {
  private val readPostfix: String

  {
    if (Character.isLowerCase(className.charAt(0))) {
      readPostfix = Character.toUpperCase(className.charAt(0)) + className.substring(1)
    }
    else {
      readPostfix = if (asRawString) ("Raw" + className) else className
    }
  }

  fun writeReadCode(methodScope: ClassScope, subtyping: Boolean, out: TextOutput) {
    if (asRawString) {
      out.append("readRawString(")
      addReaderParameter(subtyping, out)
      out.append(')')
    }
    else {
      ValueReader.addReaderParameter(subtyping, out)
      out.append(".next").append(readPostfix).append("()")
      //beginReadCall(readPostfix, subtyping, out, name);
    }
  }

  fun appendFinishedValueTypeName(out: TextOutput) {
    out.append(className)
  }

  public fun writeArrayReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    if (readPostfix == "String") {
      out.append("nextList")
    }
    else {
      out.append("read").append(readPostfix).append("Array")
    }
    out.append('(').append(Util.READER_NAME)
    out.append(')')
  }
}
