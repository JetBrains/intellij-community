package org.jetbrains.protocolReader

import org.jetbrains.jsonProtocol.JsonReaders

internal fun appendEnums(enumConstants: List<String>, enumName: String, input: Boolean, out: TextOutput) {
  out.append("enum class ").append(enumName)

  if (!input) {
    out.append("(private val protocolValue: String)")
  }

  out.openBlock()
  for (constant in enumConstants) {
    out.append(JsonReaders.convertRawEnumName(constant))
    if (!input) {
      out.append("(\"").append(constant).append("\")")
      if (enumConstants.get(enumConstants.size - 1) != constant) {
        out.comma()
      }
    }
    else {
      out.comma()
    }
  }

  if (input) {
    out.append("NO_ENUM_CONST")
  }
  else {
    out.append(';').newLine().newLine().append("override fun toString() = protocolValue")
  }
  out.closeBlock()
}