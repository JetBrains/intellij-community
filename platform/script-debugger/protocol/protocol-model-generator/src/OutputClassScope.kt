package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.jsonProtocol.ProtocolMetaModel
import org.jetbrains.protocolReader.TextOutput

internal class OutputClassScope(generator: DomainGenerator, classNamePath: NamePath) : ClassScope(generator, classNamePath) {
  fun <P : ItemDescriptor.Named> writeWriteCalls(out: TextOutput, parameters: List<Pair<P, BoxableType>>, qualifier: String?) {
    for ((descriptor, type) in parameters) {
      out.newLine()
      if (qualifier != null) {
        out.append(qualifier).append('.')
      }
      appendWriteValueInvocation(out, descriptor, descriptor.name(), type)
    }
  }

  fun <P : ItemDescriptor.Named> writeMethodParameters(out: TextOutput, parameters: List<Pair<P, BoxableType>>, prependComma: Boolean) {
    var needComa = prependComma
    for ((descriptor, type) in parameters) {
      if (needComa) {
        out.comma()
      }
      else {
        needComa = true
      }

      val shortText = type.getShortText(classContextNamespace)
      out.append(descriptor.name()).append(": ")
      out.append(if (shortText == "String") "CharSequence" else shortText)
      if (descriptor.optional) {
        val defaultValue: String
        if (descriptor is ProtocolMetaModel.Parameter && descriptor.default != null) {
          defaultValue = descriptor.default!!
        }
        else {
          // todo generic solution to specify default value
          defaultValue = if (descriptor.name() == "enabled") "true" else if (descriptor.name() == "maxStringLength") "100" else type.defaultValue ?: "null"
          if (defaultValue == "null") {
            out.append('?')
          }
        }
        out.append(" = ").append(defaultValue)
      }
    }
  }

  private fun appendWriteValueInvocation(out: TextOutput, descriptor: ItemDescriptor.Named, valueRefName: String, type: BoxableType) {
    // todo CallArgument (we should allow write null as value)
    val allowNullableString = descriptor.name() == "value" && type.writeMethodName == "writeString"
    out.append(if (allowNullableString) "writeNullableString" else type.writeMethodName).append('(')
    out.quote(descriptor.name()).comma().append(valueRefName)

    if (!allowNullableString && descriptor.optional && type.defaultValue != null && descriptor.name() != "depth" && type != BoxableType.MAP) {
      out.comma().append(if (descriptor.name() == "enabled") "true" else if (descriptor.name() == "maxStringLength") "100" else type.defaultValue!!)
    }

    out.append(')')
  }

  override val typeDirection = TypeData.Direction.OUTPUT
}
