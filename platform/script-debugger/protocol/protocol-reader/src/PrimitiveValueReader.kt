package org.jetbrains.protocolReader

internal open class PrimitiveValueReader(val className: String, val defaultValue: String? = null, private val asRawString: Boolean = false, private val nullable: Boolean = false) : ValueReader() {
  private val readPostfix: String

  init {
    if (Character.isLowerCase(className.get(0))) {
      readPostfix = "${Character.toUpperCase(className.get(0))}${className.substring(1)}"
    }
    else {
      readPostfix = if (asRawString) ("Raw$className") else className
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
      out.append(".next");
      if (nullable) {
        out.append("Nullable");
      }
      out.append(readPostfix).append("()")
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
    out.append('(').append(READER_NAME).append(')')
  }
}
