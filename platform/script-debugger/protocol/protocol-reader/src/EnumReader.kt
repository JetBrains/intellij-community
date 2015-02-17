package org.jetbrains.protocolReader

class EnumReader<T : Enum<T>> private(private val enumClass: Class<T>) : ValueReader() {

  public fun appendFinishedValueTypeName(out: TextOutput) {
    out.append(enumClass.getCanonicalName())
  }

  fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    beginReadCall("Enum", subtyping, out)
    out.comma().append(enumClass.getCanonicalName()).append(".class").append(')')
  }

  class object {
    public fun <T : Enum<T>> create(enumTypeClass: Class<T>): EnumReader<T> {
      return EnumReader(enumTypeClass)
    }
  }
}
