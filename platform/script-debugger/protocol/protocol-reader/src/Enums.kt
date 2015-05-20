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
    out.newLine().append("private final String protocolValue;").newLine()
    out.newLine().append(enumName).append("(String protocolValue)").openBlock()
    out.append("this.protocolValue = protocolValue;").closeBlock()
    out.newLine().newLine().append("public String toString()").openBlock()
    out.append("return protocolValue;").closeBlock()
    out.closeBlock()
  }
}