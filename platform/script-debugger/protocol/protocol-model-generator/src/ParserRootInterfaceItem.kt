package org.jetbrains.protocolModelGenerator

import org.jetbrains.protocolReader.JSON_READER_PARAMETER_DEF
import org.jetbrains.protocolReader.TextOutput

class ParserRootInterfaceItem(val domain: String, val name: String, private val nameScheme: ClassNameScheme.Input) : Comparable<ParserRootInterfaceItem> {
  val fullName: String

  init {
    fullName = nameScheme.getFullName(domain, name).getFullText()
  }

  fun writeCode(out: TextOutput) {
    out.append("@org.jetbrains.jsonProtocol.JsonParseMethod").newLine()
    out.append("public abstract ").append(fullName).space()
    appendReadMethodName(out)
    out.append("(").append(JSON_READER_PARAMETER_DEF).append(")").semi().newLine()
  }

  fun appendReadMethodName(out: TextOutput) {
    out.append(nameScheme.getParseMethodName(domain, name))
  }

  override fun compareTo(other: ParserRootInterfaceItem): Int {
    return fullName.compareTo(other.fullName)
  }
}
