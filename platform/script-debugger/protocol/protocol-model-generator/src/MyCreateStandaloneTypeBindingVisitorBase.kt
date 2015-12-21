package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ProtocolMetaModel
import org.jetbrains.protocolReader.appendEnums

internal class MyCreateStandaloneTypeBindingVisitorBase(private val generator: DomainGenerator, type: ProtocolMetaModel.StandaloneType, private val name: String) : CreateStandaloneTypeBindingVisitorBase(generator, type) {
  override fun visitObject(properties: List<ProtocolMetaModel.ObjectProperty>?): StandaloneTypeBinding {
    return object : StandaloneTypeBinding {
      override fun getJavaType() = subMessageType(generator.generator.naming.additionalParam.getFullName(generator.domain.domain(), name))

      override fun generate() = generator.generateCommandAdditionalParam(type)

      override fun getDirection() = TypeData.Direction.OUTPUT
    }
  }

  override fun visitEnum(enumConstants: List<String>): StandaloneTypeBinding {
    return object : StandaloneTypeBinding {
      override fun getJavaType(): BoxableType = StandaloneType(generator.generator.naming.additionalParam.getFullName(generator.domain.domain(), name), "writeEnum")

      override fun generate() = appendEnums(enumConstants, name, false, generator.fileUpdater.out.newLine().newLine())

      override fun getDirection() = TypeData.Direction.OUTPUT
    }
  }

  override fun visitArray(items: ProtocolMetaModel.ArrayItemType) = generator.createTypedefTypeBinding(type, object : Target {
    override fun resolve(context: Target.ResolveContext): BoxableType {
      return ListType(generator.generator.resolveType(items, object : ResolveAndGenerateScope {
        // This class is responsible for generating ad hoc type.
        // If we ever are to do it, we should generate into string buffer and put strings inside TypeDef class
        override fun getDomainName() = generator.domain.domain()

        override fun getTypeDirection() = TypeData.Direction.OUTPUT

        override fun generateNestedObject(description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?) = context.generateNestedObject("Item", description, properties)
      }).type)
    }
  }, generator.generator.naming.outputTypedef, TypeData.Direction.OUTPUT)
}
