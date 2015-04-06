package org.jetbrains.protocolModelGenerator

import org.jetbrains.io.JsonReaderEx
import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.jsonProtocol.ProtocolMetaModel
import org.jetbrains.protocolReader.TextOutput
import org.jetbrains.protocolReader.appendEnums
import java.util.ArrayList

class OutputClassScope(generator: DomainGenerator, classNamePath: NamePath) : ClassScope(generator, classNamePath) {
  fun <P : ItemDescriptor.Named> generate(out: TextOutput, parameters: List<P>?) {
    if (parameters == null) {
      return
    }

    val mandatoryParameters = ArrayList<P>()
    val optionalParameters = ArrayList<P>()
    for (parameter in parameters) {
      if (parameter.optional()) {
        optionalParameters.add(parameter)
      }
      else {
        mandatoryParameters.add(parameter)
      }
    }

    if (!mandatoryParameters.isEmpty()) {
      generateConstructor(out, mandatoryParameters)
      if (mandatoryParameters.size() == 1) {
        val parameter = mandatoryParameters.get(0)
        val typeData = MemberScope(this, parameter.getName()).resolveType<P>(parameter)
        if (typeData.type.getFullText() == "int[]") {
          val types = arrayOfNulls<BoxableType>(mandatoryParameters.size())
          types[0] = object : ListType(BoxableType.INT) {
            override fun getShortText(contextNamespace: NamePath): String {
              return getFullText()
            }

            override fun getFullText(): String {
              return "gnu.trove.TIntArrayList"
            }

            override val writeMethodName = "writeIntList"
          }

          out.newLine().newLine()
          generateConstructor(out, mandatoryParameters, types)

          types[0] = object : ListType(BoxableType.INT) {
            override fun getShortText(contextNamespace: NamePath): String {
              return getFullText()
            }

            override fun getFullText() = "int"

            override val writeMethodName = "writeSingletonIntArray"
          }

          out.newLine().newLine()
          generateConstructor(out, mandatoryParameters, types)
        }
      }
    }

    for (parameter in optionalParameters) {
      out.newLine().newLine()
      if (parameter.description() != null) {
        out.append("/**").newLine().append(" * @param v ").append(parameter.description()!!).newLine().append(" */").newLine()
      }

      var type: CharSequence = MemberScope(this, parameter.name()).resolveType<P>(parameter).type.getShortText(classContextNamespace)
      if (type == javaClass<JsonReaderEx>().getCanonicalName()) {
        type = "String"
      }

      out.append("public ").append(classContextNamespace.lastComponent)
      out.space().append(parameter.name()).append("(").append(type)
      out.space().append("v").append(")").openBlock()
      appendWriteValueInvocation(out, parameter, "v")
      out.newLine().append("return this;")
      out.closeBlock()
    }
  }

  private fun <P : ItemDescriptor.Named> generateConstructor(out: TextOutput, parameters: List<P>, parameterTypes: Array<BoxableType?> = arrayOfNulls<BoxableType>(parameters.size())) {
    var hasDoc = false
    for (parameter in parameters) {
      if (parameter.description() != null) {
        hasDoc = true
        break
      }
    }
    if (hasDoc) {
      out.append("/**").newLine()
      for (parameter in parameters) {
        if (parameter.description() != null) {
          out.append(" * @param " + parameter.name() + ' ' + parameter.description()).newLine()
        }
      }
      out.append(" */").newLine()
    }
    out.append("public " + classContextNamespace.lastComponent + '(')

    writeMethodParameters(out, parameters, parameterTypes)
    out.append(')')

    out.openBlock(false)
    writeWriteCalls(out, parameters, parameterTypes, null)
    out.closeBlock()
  }

  fun <P : ItemDescriptor.Named> writeWriteCalls(out: TextOutput, parameters: List<P>, parameterTypes: Array<BoxableType?>, qualifier: String?) {
    for (i in 0..parameters.size() - 1) {
      out.newLine()

      if (qualifier != null) {
        out.append(qualifier).append('.')
      }

      val parameter = parameters.get(i)
      appendWriteValueInvocation(out, parameter, parameter.name(), parameterTypes[i]!!)
    }
  }

  fun <P : ItemDescriptor.Named> writeMethodParameters(out: TextOutput, parameters: List<P>, parameterTypes: Array<BoxableType?>) {
    for (i in 0..parameterTypes.size() - 1) {
      if (parameterTypes[i] == null) {
        val parameter = parameters.get(i)
        parameterTypes[i] = MemberScope(this, parameter.name()).resolveType<P>(parameter).type
      }
    }

    var needComa = false
    val size = parameters.size()
    for (i in 0..size - 1) {
      if (needComa) {
        out.comma()
      }
      else {
        needComa = true
      }

      val shortText = parameterTypes[i]!!.getShortText(classContextNamespace)
      out.append(if (shortText == "String") "CharSequence" else shortText)
      out.space().append(parameters.get(i).name())
    }
  }

  private fun appendWriteValueInvocation(out: TextOutput, parameter: ItemDescriptor.Named, valueRefName: String, type: BoxableType = MemberScope(this, parameter.name()).resolveType<ItemDescriptor.Named>(parameter).type) {
    var blockOpened = false
    if (parameter.optional()) {
      val nullValue: String?
      if (parameter.name() == "columnNumber" || parameter.name() == "column") {
        // todo generic solution
        nullValue = "-1"
      }
      else {
        nullValue = null
      }

      if (nullValue != null) {
        blockOpened = true
        out.append("if (v != ").append(nullValue).append(")").openBlock()
      }
      else if (parameter.name() == "enabled") {
        blockOpened = true
        out.append("if (!v)").openBlock()
      }
      else if (parameter.name() == "ignoreCount") {
        blockOpened = true
        out.append("if (v > 0)").openBlock()
      }
    }
    // todo CallArgument (we should allow write null as value)
    out.append(if (parameter.name() == "value" && type.writeMethodName == "writeString") "writeNullableString" else type.writeMethodName).append("(")
    out.quote(parameter.name()).comma().append(valueRefName).append(");")
    if (blockOpened) {
      out.closeBlock()
    }
  }

  override val typeDirection = TypeData.Direction.OUTPUT
}
