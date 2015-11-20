package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.protocolReader.TextOutput

internal class OutputClassScope(generator: DomainGenerator, classNamePath: NamePath) : ClassScope(generator, classNamePath) {
//  fun <P : ItemDescriptor.Named> generateInit(out: TextOutput, mandatoryParameters: List<P>, mandatoryParameterTypes: Array<BoxableType?>) {
//    writeWriteCalls(out, mandatoryParameters, mandatoryParameterTypes, null)
//
//    if (mandatoryParameters.size == 1) {
//      val parameter = mandatoryParameters.get(0)
//      val typeData = MemberScope(this, parameter.getName()).resolveType(parameter)
//      if (typeData.type.getFullText() == "int[]") {
//        val types = arrayOfNulls<BoxableType>(mandatoryParameters.size)
//        types[0] = object : ListType(BoxableType.INT) {
//          override fun getShortText(contextNamespace: NamePath): String {
//            return getFullText()
//          }
//
//          override fun getFullText(): String {
//            return "gnu.trove.TIntArrayList"
//          }
//
//          override val writeMethodName = "writeIntList"
//        }
//
//        out.newLine().newLine()
//        generateConstructor(out, mandatoryParameters, types)
//
//        types[0] = object : ListType(BoxableType.INT) {
//          override fun getShortText(contextNamespace: NamePath) = getFullText()
//
//          override fun getFullText() = "int"
//
//          override val writeMethodName = "writeSingletonIntArray"
//        }
//
//        out.newLine().newLine()
//        generateConstructor(out, mandatoryParameters, types)
//      }
//    }
//  }

//  fun <P : ItemDescriptor.Named> generate(out: TextOutput, optionalParameters: List<P>) {
//    for (parameter in optionalParameters) {
//      out.newLine().newLine()
//      if (parameter.description() != null) {
//        out.append("/**").newLine().append(" * @param v ").append(parameter.description()!!).newLine().append(" */").newLine()
//      }
//
//      val typeDescriptor = MemberScope(this, parameter.name()).resolveType(parameter)
//      var type = typeDescriptor.type.getShortText(classContextNamespace)
//      if (type == JsonReaderEx::class.java.canonicalName) {
//        type = "String"
//      }
//
//
//      out.append("fun ").appendEscapedName(parameter.name()).append("(v: ").append(type)
//      if (typeDescriptor.isNullableType) {
//        out.append("?")
//      }
//      out.append("): ").append(classContextNamespace.lastComponent).block() {
//        appendWriteValueInvocation(out, parameter, "v")
//        out.newLine().append("return this")
//      }
//    }
//  }

  private fun <P : ItemDescriptor.Named> generateConstructor(out: TextOutput, parameters: List<P>, parameterTypes: Array<BoxableType?> = arrayOfNulls<BoxableType>(parameters.size)) {
//    var hasDoc = false
//    for (parameter in parameters) {
//      if (parameter.description() != null) {
//        hasDoc = true
//        break
//      }
//    }
//    if (hasDoc) {
//      out.append("/**").newLine()
//      for (parameter in parameters) {
//        if (parameter.description() != null) {
//          out.append(" * @param " + parameter.name() + ' ' + parameter.description()).newLine()
//        }
//      }
//      out.append(" */").newLine()
//    }
//
//    out.newLine().append("init")
//    out.block {
//      writeWriteCalls(out, parameters, parameterTypes, null)
//    }
  }

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
      if (descriptor.optional()) {
        // todo generic solution to specify default value
        val defaultValue = if (descriptor.name() == "enabled") "true" else if (descriptor.name() == "maxStringLength") "100" else type.defaultValue ?: "null"
        if (defaultValue == "null") {
          out.append('?')
        }
        out.append(" = ").append(defaultValue)
      }
    }
  }

  private fun appendWriteValueInvocation(out: TextOutput, descriptor: ItemDescriptor.Named, valueRefName: String, type: BoxableType) {
//    if (parameter.optional()) {
//      val nullValue = type.defaultValue
////      if (parameter.name() == "columnNumber" || parameter.name() == "column") {
////        // todo generic solution
////        nullValue = "-1"
////      }
////      else {
////        nullValue = null
////      }
//
////      if (nullValue != null) {
////        blockOpened = true
////        out.append("if (v != ").append(nullValue).append(")").openBlock()
////      }
////      else if (parameter.name() == "enabled") {
////        blockOpened = true
////        out.append("if (!v)").openBlock()
////      }
////      else if (parameter.name() == "ignoreCount") {
////        blockOpened = true
////        out.append("if (v > 0)").openBlock()
////      }
//    }
    // todo CallArgument (we should allow write null as value)
    val allowNullableString = descriptor.name() == "value" && type.writeMethodName == "writeString"
    out.append(if (allowNullableString) "writeNullableString" else type.writeMethodName).append('(')
    out.quote(descriptor.name()).comma().append(valueRefName)

    if (!allowNullableString && descriptor.optional() && type.defaultValue != null && descriptor.name() != "depth") {
      out.comma().append(if (descriptor.name() == "enabled") "true" else if (descriptor.name() == "maxStringLength") "100" else type.defaultValue!!)
    }

    out.append(')')
  }

  override val typeDirection = TypeData.Direction.OUTPUT
}
