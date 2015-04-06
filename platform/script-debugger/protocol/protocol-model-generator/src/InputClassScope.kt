package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.jsonProtocol.ProtocolMetaModel
import org.jetbrains.protocolReader.TextOutput
import org.jetbrains.protocolReader.appendEnums

class InputClassScope(generator: DomainGenerator, namePath: NamePath) : ClassScope(generator, namePath) {
  fun generateDeclarationBody(out: TextOutput, list: List<ItemDescriptor.Named>) {
    for (i in 0..list.size() - 1) {
      val named = list.get(i)
      if (named.description() != null) {
        out.doc(named.description())
      }

      val name = named.getName()
      val declarationName = generateMethodNameSubstitute(name, out)
      val typeDescriptor = InputMemberScope(name).resolveType<ItemDescriptor.Named>(named)
      typeDescriptor.writeAnnotations(out)
      out.append(typeDescriptor.type.getShortText(classContextNamespace)).space().append(declarationName).append("();")
      if (i != (list.size() - 1)) {
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
          out.newLine().doc(description)
          if (properties == null) {
            out.append("@org.jetbrains.jsonProtocol.JsonType(allowsOtherProperties=true)").newLine()
            out.append("public interface ").append(objectName).append(" extends org.jetbrains.jsonProtocol.JsonObjectBased").openBlock()
          }
          else {
            out.append("@org.jetbrains.jsonProtocol.JsonType").newLine()
            out.append("public interface ").append(objectName).openBlock()
            for (property in properties) {
              out.doc(property.description())

              val methodName = generateMethodNameSubstitute(property.getName(), out)
              val memberScope = InputMemberScope(property.getName())
              val propertyTypeData = memberScope.resolveType<ProtocolMetaModel.ObjectProperty>(property)
              propertyTypeData.writeAnnotations(out)

              out.append(propertyTypeData.type.getShortText(classContextNamespace) + ' ' + methodName + "();").newLine()
            }
          }
          out.closeBlock()
        }
      })
      return StandaloneType(NamePath(objectName, classContextNamespace), "writeMessage")
    }
  }
}
