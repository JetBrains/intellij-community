package org.jetbrains.protocolReader

import org.jetbrains.io.JsonReaderEx
import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.jsonProtocol.ProtocolMetaModel
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
        val typeData = OutputMemberScope(ClassScope.getName(parameter)).resolveType<P>(parameter)
        if (typeData.type.getFullText() == "int[]") {
          val types = arrayOfNulls<BoxableType>(mandatoryParameters.size())
          types[0] = object : ListType(BoxableType.INT) {
            override fun getShortText(contextNamespace: NamePath): String {
              return getFullText()
            }

            override fun getFullText(): String {
              return "gnu.trove.TIntArrayList"
            }

            override fun getWriteMethodName(): String {
              return "writeIntList"
            }
          }

          out.newLine().newLine()
          generateConstructor(out, mandatoryParameters, types)

          types[0] = object : ListType(BoxableType.INT) {
            override fun getShortText(contextNamespace: NamePath): String {
              return getFullText()
            }

            override fun getFullText(): String {
              return "int"
            }

            override fun getWriteMethodName(): String {
              return "writeSingletonIntArray"
            }
          }

          out.newLine().newLine()
          generateConstructor(out, mandatoryParameters, types)
        }
      }
    }

    // generate enum classes after constructor
    for (parameter in parameters) {
      if (parameter.getEnum() != null) {
        out.newLine().newLine()
        appendEnumClass(out, parameter.description(), parameter.getEnum()!!, capitalizeFirstChar((parameter.name())))
      }
    }

    for (parameter in optionalParameters) {
      out.newLine().newLine()
      if (parameter.description() != null) {
        out.append("/**").newLine().append(" * @param v ").append(parameter.description()!!).newLine().append(" */").newLine()
      }

      var type: CharSequence = OutputMemberScope(parameter.name()).resolveType<P>(parameter).type.getShortText(classContextNamespace)
      if (type == javaClass<JsonReaderEx>().getCanonicalName()) {
        type = "String"
      }

      out.append("public ").append(getShortClassName())
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
    out.append("public " + getShortClassName() + '(')

    writeMethodParameters(out, parameters, parameterTypes)
    out.append(')')

    out.openBlock(false)
    writeWriteCalls(out, parameters, parameterTypes, null)
    out.closeBlock()
  }

  fun <P : ItemDescriptor.Named> writeWriteCalls(out: TextOutput, parameters: List<P>, parameterTypes: Array<BoxableType?>, qualifier: String?) {
    run {
      var i = 0
      val size = parameters.size()
      while (i < size) {
        out.newLine()

        if (qualifier != null) {
          out.append(qualifier).append('.')
        }

        val parameter = parameters.get(i)
        appendWriteValueInvocation(out, parameter, parameter.name(), parameterTypes[i]!!)
        i++
      }
    }
  }

  fun <P : ItemDescriptor.Named> writeMethodParameters(out: TextOutput, parameters: List<P>, parameterTypes: Array<BoxableType?>) {
    run {
      var i = 0
      val length = parameterTypes.size()
      while (i < length) {
        if (parameterTypes[i] == null) {
          val parameter = parameters.get(i)
          parameterTypes[i] = OutputMemberScope(parameter.name()).resolveType<P>(parameter).type
        }
        i++
      }
    }

    var needComa = false
    run {
      var i = 0
      val size = parameters.size()
      while (i < size) {
        if (needComa) {
          out.comma()
        }
        else {
          needComa = true
        }

        out.append(parameterTypes[i]!!.getShortText(classContextNamespace))
        out.space().append(parameters.get(i).name())
        i++
      }
    }
  }

  private fun appendWriteValueInvocation(out: TextOutput, parameter: ItemDescriptor.Named, valueRefName: String, type: BoxableType = OutputMemberScope(parameter.name()).resolveType<ItemDescriptor.Named>(parameter).type) {
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
    out.append(if (parameter.name() == "value" && type.getWriteMethodName() == "writeString") "writeNullableString" else type.getWriteMethodName()).append("(")
    out.quote(parameter.name()).comma().append(valueRefName).append(");")
    if (blockOpened) {
      out.closeBlock()
    }
  }

  override fun getTypeDirection(): TypeData.Direction {
    return TypeData.Direction.OUTPUT
  }

  inner class OutputMemberScope(memberName: String) : MemberScope(this@OutputClassScope, memberName) {
    override fun generateEnum(description: String?, enumConstants: List<String>): BoxableType {
      return StandaloneType(NamePath(capitalizeFirstChar(memberName), classContextNamespace), "writeEnum")
    }

    override fun generateNestedObject(description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?) = throw UnsupportedOperationException()
  }

  private fun appendEnumClass(out: TextOutput, description: String?, enumConstants: List<String>, enumName: String) {
    out.doc(description)
    appendEnums(enumConstants, enumName, false, out)
    out.newLine().append("private final String protocolValue;").newLine()
    out.newLine().append(enumName).append("(String protocolValue)").openBlock()
    out.append("this.protocolValue = protocolValue;").closeBlock()

    out.newLine().newLine().append("public String toString()").openBlock()
    out.append("return protocolValue;").closeBlock()
    out.closeBlock()
  }
}
