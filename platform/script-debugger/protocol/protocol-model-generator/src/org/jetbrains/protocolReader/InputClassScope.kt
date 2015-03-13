package org.jetbrains.protocolReader

class InputClassScope(generator: DomainGenerator, namePath: NamePath) : ClassScope(generator, namePath) {

  fun generateDeclarationBody(out: TextOutput, list: List<Named>) {
    run {
      var i = 0
      val n = list.size()
      while (i < n) {
        val named = list.get(i)
        if (named.description() != null) {
          out.doc(named.description())
        }

        val name = ClassScope.getName(named)
        val declarationName = Generator.generateMethodNameSubstitute(name, out)
        val typeDescriptor = InputMemberScope(name).resolveType<Named>(named)
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
      val enumName = Generator.capitalizeFirstChar(memberName)
      addMember(object : TextOutConsumer {
        override fun append(out: TextOutput) {
          out.newLine().doc(description)
          Enums.appendEnums(enumConstants, enumName, true, out)
        }
      })
      return StandaloneType(NamePath(enumName, classContextNamespace), "writeEnum")
    }

    override fun generateNestedObject(description: String?, properties: List<ObjectProperty>?): BoxableType {
      val objectName = Generator.capitalizeFirstChar(memberName)
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

              val methodName = Generator.generateMethodNameSubstitute(ClassScope.getName(property), out)
              val memberScope = InputMemberScope(ClassScope.getName(property))
              val propertyTypeData = memberScope.resolveType<ObjectProperty>(property)
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
