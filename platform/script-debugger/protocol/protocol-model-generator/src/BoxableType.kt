package org.jetbrains.protocolModelGenerator

interface BoxableType {
  val defaultValue: String?

  val fullText: CharSequence

  fun getShortText(contextNamespace: NamePath): String

  val writeMethodName: String

  companion object {
    val STRING: StandaloneType = StandaloneType(NamePath("String"), "writeString", "null")
    val ANY_STRING: StandaloneType = StandaloneType(NamePath("String"), "writeString", "null")
    val INT: StandaloneType = StandaloneType(NamePath("Int"), "writeInt", "-1")
    val LONG: StandaloneType = StandaloneType(NamePath("Long"), "writeLong", "-1")
    val NUMBER: StandaloneType = StandaloneType(NamePath("Double"), "writeDouble", "Double.NaN")
    val BOOLEAN: StandaloneType = StandaloneType(NamePath("Boolean"), "writeBoolean", "false")
    val MAP: StandaloneType = StandaloneType(NamePath("Map<String, String>"), "writeMap", "null")
  }
}