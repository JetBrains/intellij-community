package org.jetbrains.protocolReader


fun PrimitiveValueReader(name: String): PrimitiveValueReader {
  return PrimitiveValueReader(name, null, false)
}

fun PrimitiveValueReader(name: String, defaultValue: String): PrimitiveValueReader {
  return PrimitiveValueReader(name, defaultValue, false)
}

open class PrimitiveValueReader(private val className: String, val defaultValue: String?, private val asRawString: Boolean) : ValueReader() {
  private val readPostfix: String

  {
    if (Character.isLowerCase(className.charAt(0))) {
      readPostfix = Character.toUpperCase(className.charAt(0)) + className.substring(1)
    }
    else {
      readPostfix = if (asRawString) ("Raw" + className) else className
    }
  }

  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    if (asRawString) {
      out.append("readRawString(")
      addReaderParameter(subtyping, out)
      out.append(')')
    }
    else {
      addReaderParameter(subtyping, out)
      out.append(".next").append(readPostfix).append("()")
      //beginReadCall(readPostfix, subtyping, out, name);
    }
  }

  override fun appendFinishedValueTypeName(out: TextOutput) {
    out.append(className)
  }

  override fun writeArrayReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    if (readPostfix == "String") {
      out.append("nextList")
    }
    else {
      out.append("read").append(readPostfix).append("Array")
    }
    out.append('(').append(READER_NAME)
    out.append(')')
  }
}
