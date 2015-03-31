package org.jetbrains.protocolModelGenerator

abstract class BoxableType {
  abstract fun getFullText(): CharSequence

  abstract fun getShortText(contextNamespace: NamePath): String

  abstract val writeMethodName: String

  companion object {
    val STRING: BoxableType = StandaloneType(NamePath("String"), "writeString")
    val ANY_STRING: BoxableType = StandaloneType(NamePath("String"), "writeString")
    val INT: BoxableType = StandaloneType(NamePath("int"), "writeInt")
    val LONG: BoxableType = StandaloneType(NamePath("long"), "writeLong")
    val NUMBER: BoxableType = StandaloneType(NamePath("double"), "writeDouble")
    val BOOLEAN: BoxableType = StandaloneType(NamePath("boolean"), "writeBoolean")
    val MAP: BoxableType = StandaloneType(NamePath("java.util.Map<String, String>"), "writeMap")
  }
}