package org.jetbrains.protocolReader

import org.jetbrains.jsonProtocol.JsonReaders

fun appendEnums(enumConstants: List<String>, enumName: String, input: Boolean, out: TextOutput) {
  out.append("public enum ").append(enumName).openBlock()
  for (constant in enumConstants) {
    out.append(JsonReaders.convertRawEnumName(constant))
    if (!input) {
      out.append("(\"").append(constant).append("\")")
      if (enumConstants.get(enumConstants.size() - 1) != constant) {
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

  if (input) {
    out.closeBlock()
  }
  else {
    out.semi().newLine()
  }
}