package org.jetbrains.protocolReader

import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.jsonProtocol.ProtocolMetaModel

class InputClassScope(generator: DomainGenerator, namePath: NamePath) : ClassScope(generator, namePath) {

  fun generateDeclarationBody(out: TextOutput, list: List<ItemDescriptor.Named>) {
    run {
      var i = 0
      val n = list.size()
      while (i < n) {
        val named = list.get(i)
        if (named.description() != null) {
          out.doc(named.description())
        }

        val name = ClassScope.getName(named)
        val declarationName = generateMethodNameSubstitute(name, out)
        val typeDescriptor = InputMemberScope(name).resolveType<ItemDescriptor.Named>(named)
        typeDescriptor.writeAnnotations(out)
        out.append(typeDescriptor.type.getShortText(classContextNamespace)).space().append(declarationName).append("();")
        if (i != (n - 1)) {
          out.newLine().newLine()
        }
        i++
      }
    }
  }

  override fun getTypeDirection(): TypeData.Direction {
    return TypeData.Direction.INPUT
  }

  inner class InputMemberScope(memberName: String) : MemberScope(this@InputClassScope, memberName) {
    override fun generateEnum(description: String?, enumConstants: List<String>): BoxableType {
      val enumName = capitalizeFirstChar(memberName)
      addMember(object : TextOutConsumer {
        override fun append(out: TextOutput) {
          out.newLine().doc(description)
          appendEnums(enumConstants, enumName, true, out)
        }
      })
      return StandaloneType(NamePath(enumName, classContextNamespace), "writeEnum")
    }

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

              val methodName = generateMethodNameSubstitute(ClassScope.getName(property), out)
              val memberScope = InputMemberScope(ClassScope.getName(property))
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
