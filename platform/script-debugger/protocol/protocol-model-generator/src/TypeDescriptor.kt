package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.jsonProtocol.ProtocolMetaModel
import org.jetbrains.protocolReader.TextOutput

internal class TypeDescriptor(val type: BoxableType, val descriptor: ItemDescriptor, private val asRawString: Boolean = false) {
  private val optional = descriptor is ItemDescriptor.Named && descriptor.optional

  fun writeAnnotations(out: TextOutput) {
    val default = (descriptor as? ProtocolMetaModel.Parameter)?.default
    if (optional || default != null) {
      out.append("@Optional")
      if (default != null) {
        out.append("(\"").append(default).append("\")")
      }
      out.newLine()
    }

    if (asRawString) {
      out.append("@org.jetbrains.jsonProtocol.JsonField(")
      if (asRawString) {
        out.append("allowAnyPrimitiveValue=true")
      }
      out.append(")").newLine()
    }
  }

  val isNullableType: Boolean
    get() = type != BoxableType.BOOLEAN && type != BoxableType.INT && type != BoxableType.Companion.LONG && (optional || asRawString)
}
