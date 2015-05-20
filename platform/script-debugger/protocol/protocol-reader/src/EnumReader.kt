package org.jetbrains.protocolReader

class EnumReader(private val enumClass: Class<Enum<*>>) : ValueReader() {
  override fun appendFinishedValueTypeName(out: TextOutput) {
    out.append(enumClass.getCanonicalName())
  }

  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    beginReadCall("Enum", subtyping, out)
    out.comma().append(enumClass.getCanonicalName()).append(".class").append(')')
  }
}
