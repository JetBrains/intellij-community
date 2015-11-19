package org.jetbrains.protocolModelGenerator

interface BoxableType {
  val defaultValue: String?

  val fullText: CharSequence

  fun getShortText(contextNamespace: NamePath): String

  val writeMethodName: String

  companion object {
    val STRING = StandaloneType(NamePath("String"), "writeString", "null")
    val ANY_STRING = StandaloneType(NamePath("String"), "writeString", "null")
    val INT = StandaloneType(NamePath("Int"), "writeInt", "-1")
    val LONG = StandaloneType(NamePath("Long"), "writeLong", "-1")
    val NUMBER = StandaloneType(NamePath("Double"), "writeDouble", "Double.NaN")
    val BOOLEAN = StandaloneType(NamePath("Boolean"), "writeBoolean", "false")
    val MAP = StandaloneType(NamePath("Map<String, String>"), "writeMap", "null")
  }
}