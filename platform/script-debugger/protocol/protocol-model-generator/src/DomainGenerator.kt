package org.jetbrains.protocolModelGenerator

import com.intellij.util.SmartList
import com.intellij.util.containers.isNullOrEmpty
import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.jsonProtocol.ProtocolMetaModel
import org.jetbrains.protocolReader.FileUpdater
import org.jetbrains.protocolReader.JSON_READER_PARAMETER_DEF
import org.jetbrains.protocolReader.TextOutput
import org.jetbrains.protocolReader.appendEnums

internal class DomainGenerator(val generator: Generator, val domain: ProtocolMetaModel.Domain, val fileUpdater: FileUpdater) {
  fun registerTypes() {
    domain.types?.let {
      for (type in it) {
        generator.typeMap.getTypeData(domain.domain(), type.id()).type = type
      }
    }
  }

  fun generateCommandsAndEvents() {
    for (command in domain.commands()) {
      val hasResponse = command.returns != null
      val returnType = if (hasResponse) generator.naming.commandResult.getShortName(command.name()) else "Unit"
      generateTopLevelOutputClass(generator.naming.params, command.name(), command.description, "${generator.naming.requestClassName}<$returnType>", {
        append('"')
        if (!domain.domain().isEmpty()) {
          append(domain.domain()).append('.')
        }
        append(command.name()).append('"')
      }, command.parameters)

      if (hasResponse) {
        generateJsonProtocolInterface(generator.naming.commandResult.getShortName(command.name()), command.description, command.returns, null)
        generator.jsonProtocolParserClassNames.add(generator.naming.commandResult.getFullName(domain.domain(), command.name()).getFullText())
        generator.parserRootInterfaceItems.add(ParserRootInterfaceItem(domain.domain(), command.name(), generator.naming.commandResult))
      }
    }

    if (domain.events != null) {
      for (event in domain.events!!) {
        generateEvenData(event)
        generator.jsonProtocolParserClassNames.add(generator.naming.eventData.getFullName(domain.domain(), event.name()).getFullText())
        generator.parserRootInterfaceItems.add(ParserRootInterfaceItem(domain.domain(), event.name(), generator.naming.eventData))
      }
    }
  }

  fun generateCommandAdditionalParam(type: ProtocolMetaModel.StandaloneType) {
    generateTopLevelOutputClass(generator.naming.additionalParam, type.id(), type.description, null, null, type.properties)
  }

  private fun <P : ItemDescriptor.Named> generateTopLevelOutputClass(nameScheme: ClassNameScheme, baseName: String, description: String?, baseType: String?, methodName: (TextOutput.() -> Unit)?, properties: List<P>?) {
    generateOutputClass(fileUpdater.out.newLine().newLine(), nameScheme.getFullName(domain.domain(), baseName), description, baseType, methodName, properties)
  }

  private fun <P : ItemDescriptor.Named> generateOutputClass(out: TextOutput, classNamePath: NamePath, description: String?, baseType: String?, methodName: (TextOutput.() -> Unit)?, properties: List<P>?) {
    out.doc(description)

    out.append(if (baseType == null) "class" else "fun").space().append(classNamePath.lastComponent).append('(')

    val classScope = OutputClassScope(this, classNamePath)
    val (mandatoryParameters, optionalParameters) = getParametersInfo(classScope, properties)
    if (properties.isNullOrEmpty()) {
      assert(baseType != null)

      out.append(") = ")
      out.append(baseType ?: "org.jetbrains.jsonProtocol.OutMessage")
      out.append('(')
      methodName?.invoke(out)
      out.append(')')
      return
    }
    else {
      classScope.writeMethodParameters(out, mandatoryParameters, false)
      classScope.writeMethodParameters(out, optionalParameters, mandatoryParameters.isNotEmpty())

      out.append(')')
      out.append(" : ").append(baseType ?: "org.jetbrains.jsonProtocol.OutMessage")
      if (baseType == null) {
        out.append("()").openBlock().append("init")
      }
    }

    out.block(baseType != null) {
      if (baseType != null) {
        out.append("val m = ").append(baseType)
        out.append('(')
        methodName?.invoke(out)
        out.append(')')
      }

      if (!properties.isNullOrEmpty()) {
        val qualifier = if (baseType == null) null else "m"
        classScope.writeWriteCalls(out, mandatoryParameters, qualifier)
        classScope.writeWriteCalls(out, optionalParameters, qualifier)
        if (baseType != null) {
          out.newLine().append("return m")
        }
      }
    }

    if (baseType == null) {
      // close class
      out.closeBlock()
    }

    classScope.writeAdditionalMembers(out)
  }

  private fun <P : ItemDescriptor.Named> getParametersInfo(classScope: OutputClassScope, properties: List<P>?): Pair<List<Pair<P, BoxableType>>, List<Pair<P, BoxableType>>> {
    if (properties.isNullOrEmpty()) {
      return Pair(emptyList(), emptyList())
    }

    val mandatoryParameters = SmartList<Pair<P, BoxableType>>()
    val optionalParameters = SmartList<Pair<P, BoxableType>>()
    if (properties != null) {
      for (parameter in properties) {
        val type = MemberScope(classScope, parameter.name()).resolveType(parameter).type
        if (parameter.optional) {
          optionalParameters.add(parameter to type)
        }
        else {
          mandatoryParameters.add(parameter to type)
        }
      }
    }
    return Pair(mandatoryParameters, optionalParameters)
  }

  fun createStandaloneOutputTypeBinding(type: ProtocolMetaModel.StandaloneType, name: String) = switchByType(type, MyCreateStandaloneTypeBindingVisitorBase(this, type, name))

  fun createStandaloneInputTypeBinding(type: ProtocolMetaModel.StandaloneType): StandaloneTypeBinding {
    return switchByType(type, object : CreateStandaloneTypeBindingVisitorBase(this, type) {
      override fun visitObject(properties: List<ProtocolMetaModel.ObjectProperty>?) = createStandaloneObjectInputTypeBinding(type, properties)

      override fun visitEnum(enumConstants: List<String>): StandaloneTypeBinding {
        val name = type.id()
        return object : StandaloneTypeBinding {
          override fun getJavaType() = StandaloneType(generator.naming.inputEnum.getFullName(domain.domain(), name), "writeEnum")

          override fun generate() {
            fileUpdater.out.doc(type.description)
            appendEnums(enumConstants, generator.naming.inputEnum.getShortName(name), true, fileUpdater.out)
          }

          override fun getDirection() = TypeData.Direction.INPUT
        }
      }

      override fun visitArray(items: ProtocolMetaModel.ArrayItemType): StandaloneTypeBinding {
        val resolveAndGenerateScope = object : ResolveAndGenerateScope {
          // This class is responsible for generating ad hoc type.
          // If we ever are to do it, we should generate into string buffer and put strings
          // inside TypeDef class.
          override fun getDomainName() = domain.domain()

          override fun getTypeDirection() = TypeData.Direction.INPUT

          override fun generateNestedObject(description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?) = throw UnsupportedOperationException()
        }

        val arrayType = ListType(generator.resolveType(items, resolveAndGenerateScope).type)
        return createTypedefTypeBinding(type, object : Target {
          override fun resolve(context: Target.ResolveContext) = arrayType
        }, generator.naming.inputTypedef, TypeData.Direction.INPUT)
      }
    })
  }

  fun createStandaloneObjectInputTypeBinding(type: ProtocolMetaModel.StandaloneType, properties: List<ProtocolMetaModel.ObjectProperty>?): StandaloneTypeBinding {
    val name = type.id()
    val fullTypeName = generator.naming.inputValue.getFullName(domain.domain(), name)
    generator.jsonProtocolParserClassNames.add(fullTypeName.getFullText())

    return object : StandaloneTypeBinding {
      override fun getJavaType() = subMessageType(fullTypeName)

      override fun generate() {
        val className = generator.naming.inputValue.getFullName(domain.domain(), name)
        val out = fileUpdater.out
        out.newLine().newLine()
        descriptionAndRequiredImport(type.description, out)

        out.append("interface ").append(className.lastComponent).openBlock()
        val classScope = InputClassScope(this@DomainGenerator, className)
        if (properties != null) {
          classScope.generateDeclarationBody(out, properties)
        }
        classScope.writeAdditionalMembers(out)
        out.closeBlock()
      }

      override fun getDirection() = TypeData.Direction.INPUT
    }
  }

  /**
   * Typedef is an empty class that just holds description and
   * refers to an actual type (such as String).
   */
  fun createTypedefTypeBinding(type: ProtocolMetaModel.StandaloneType, target: Target, nameScheme: ClassNameScheme, direction: TypeData.Direction?): StandaloneTypeBinding {
    val name = type.id()
    val typedefJavaName = nameScheme.getFullName(domain.domain(), name)
    val actualJavaType = target.resolve(object : Target.ResolveContext {
      override fun generateNestedObject(shortName: String, description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?): BoxableType {
        if (direction == null) {
          throw RuntimeException("Unsupported")
        }

        when (direction) {
          TypeData.Direction.INPUT -> throw RuntimeException("TODO")
          TypeData.Direction.OUTPUT -> generateOutputClass(TextOutput(StringBuilder()), NamePath(shortName, typedefJavaName), description, null, null, properties)
        }
        return subMessageType(NamePath(shortName, typedefJavaName))
      }
    })

    return object : StandaloneTypeBinding {
      override fun getJavaType() = actualJavaType

      override fun generate() {
      }

      override fun getDirection() = direction
    }
  }

  private fun generateEvenData(event: ProtocolMetaModel.Event) {
    val className = generator.naming.eventData.getShortName(event.name())
    val domainName = domain.domain()
    val fullName = generator.naming.eventData.getFullName(domainName, event.name()).getFullText()
    generateJsonProtocolInterface(className, event.description, event.parameters) { out ->
      out.newLine().append("companion object TYPE : org.jetbrains.jsonProtocol.EventType<").append(fullName)
      if (event.optionalData || event.parameters.isNullOrEmpty()) {
        out.append('?')
      }
      out.append(", ").append(generator.naming.inputPackage).append('.').append(READER_INTERFACE_NAME).append('>')
      out.append("(\"")
      if (!domainName.isNullOrEmpty()) {
        out.append(domainName).append('.')
      }
      out.append(event.name()).append("\")").block() {
        out.append("override fun read(protocolReader: ")
        out.append(generator.naming.inputPackage).append('.').append(READER_INTERFACE_NAME).append(", ").append(JSON_READER_PARAMETER_DEF).append(")")
        out.append(" = protocolReader.").append(generator.naming.eventData.getParseMethodName(domainName, event.name())).append("(reader)")
      }
    }
  }

  private fun generateJsonProtocolInterface(className: String, description: String?, parameters: List<ProtocolMetaModel.Parameter>?, additionalMembersText: ((out: TextOutput) -> Unit)?) {
    val out = fileUpdater.out
    out.newLine().newLine()
    descriptionAndRequiredImport(description, out)

    var hasNodeId = false
    val parametersToAdd = parameters?.filter(fun(p: ProtocolMetaModel.Parameter): Boolean {
      if (p.name() == "nodeId" && p.ref == "NodeId") {
        hasNodeId = true
        return false
      }
      else return true
    })
    out.append("interface ").append(className)
    if (hasNodeId) out.append(" : ").append("org.jetbrains.wip.protocol.NodeIdentifiable")
    out.block {
      val classScope = InputClassScope(this, NamePath(className, NamePath(getPackageName(generator.naming.inputPackage, domain.domain()))))
      if (additionalMembersText != null) {
        classScope.addMember(additionalMembersText)
      }
      if (parametersToAdd != null) {
        classScope.generateDeclarationBody(out, parametersToAdd)
      }
      classScope.writeAdditionalMembers(out)
    }
  }

  private fun descriptionAndRequiredImport(description: String?, out: TextOutput) {
    if (description != null) {
      out.doc(description)
    }
//    out.append("@JsonType").newLine()
  }
}

fun subMessageType(namePath: NamePath): StandaloneType = StandaloneType(namePath, "writeMessage", null)