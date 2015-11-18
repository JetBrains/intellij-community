package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.jsonProtocol.ProtocolMetaModel
import org.jetbrains.protocolReader.TextOutput

internal class InputClassScope(generator: DomainGenerator, namePath: NamePath) : ClassScope(generator, namePath) {
  fun generateDeclarationBody(out: TextOutput, list: List<ItemDescriptor.Named>) {
    for (i in 0..list.size - 1) {
      val named = list.get(i)
      if (named.description() != null) {
        out.doc(named.description())
      }

      val name = named.getName()
      val declarationName = generateMethodNameSubstitute(name, out)
      val typeDescriptor = InputMemberScope(name).resolveType(named)
      typeDescriptor.writeAnnotations(out)
      out.append("fun ").appendEscapedName(declarationName).append("(): ").append(typeDescriptor.type.getShortText(classContextNamespace))
      if (typeDescriptor.isNullableType) {
        out.append('?')
      }
      if (i != (list.size - 1)) {
        out.newLine().newLine()
      }
    }
  }

  override val typeDirection = TypeData.Direction.INPUT

  inner class InputMemberScope(memberName: String) : MemberScope(this@InputClassScope, memberName) {
    override fun generateNestedObject(description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?): BoxableType {
      val objectName = capitalizeFirstChar(memberName)
      addMember(object : TextOutConsumer {
        override fun append(out: TextOutput) {
          out.newLine().newLine().doc(description)
          if (properties == null) {
            out.append("@JsonType(allowsOtherProperties=true)").newLine()
            out.append("interface ").append(objectName).append(" : JsonObjectBased").openBlock()
          }
          else {
            out.append("@JsonType").newLine()
            out.append("interface ").append(objectName).openBlock()
            for (property in properties) {
              out.doc(property.description())

              val methodName = generateMethodNameSubstitute(property.getName(), out)
              val memberScope = InputMemberScope(property.getName())
              val propertyTypeData = memberScope.resolveType(property)
              propertyTypeData.writeAnnotations(out)

              out.append("fun ").appendEscapedName(methodName).append("(): ").append(propertyTypeData.type.getShortText(classContextNamespace))
            }
          }
          out.closeBlock()
        }
      })
      return subMessageType(NamePath(objectName, classContextNamespace))
    }
  }
}
