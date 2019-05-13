package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.jsonProtocol.ProtocolMetaModel
import org.jetbrains.protocolReader.TextOutput

internal class InputClassScope(generator: DomainGenerator, namePath: NamePath) : ClassScope(generator, namePath) {
  override val typeDirection = TypeData.Direction.INPUT

  fun generateDeclarationBody(out: TextOutput, list: List<ItemDescriptor.Named>) {
    for (i in 0 until list.size) {
      val named = list[i]
      if (named.description != null) {
        out.doc(named.description)
      }

      val name = named.getName()
      val declarationName = generateMethodNameSubstitute(name, out)

      if (classContextNamespace.lastComponent == "RemoteObjectValue" && name == "value") {
        // specification says it is string, but it can be map or any other JSON too, so set Any? type
        out.append("@org.jetbrains.jsonProtocol.JsonField(allowAnyPrimitiveValue=true)\n" +
                   "  @Optional\n" +
                   "  fun value(): Any?\n\n  ")
        continue
      }

      val typeDescriptor = InputMemberScope(name).resolveType(named)
      writeMember(out, typeDescriptor, declarationName)
      if (i != (list.size - 1)) {
        out.newLine().newLine()
      }
    }
  }

  inner class InputMemberScope(memberName: String) : MemberScope(this@InputClassScope, memberName) {
    override fun generateNestedObject(description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?): BoxableType {
      val objectName = capitalizeFirstChar(memberName)
      addMember { out ->
        out.newLine().newLine().doc(description)
        if (properties == null) {
          out.append("interface ").append(objectName).append(" : JsonObjectBased").openBlock()
        }
        else {
          out.append("@JsonType").newLine()
          out.append("interface ").append(objectName).openBlock()
          for (property in properties) {
            out.doc(property.description)

            val methodName = generateMethodNameSubstitute(property.getName(), out)
            val memberScope = InputMemberScope(property.getName())
            val typeDescriptor = memberScope.resolveType(property)
            writeMember(out, typeDescriptor, methodName)
          }
        }
        out.closeBlock()
      }
      return subMessageType(NamePath(objectName, classContextNamespace))
    }
  }

  private fun writeMember(out: TextOutput, typeDescriptor: TypeDescriptor, name: String) {
    typeDescriptor.writeAnnotations(out)
    val asProperty = typeDescriptor.isPrimitive || typeDescriptor.isNullableType
    out.append(if (asProperty) "val" else "fun")
    out.append(" ").appendEscapedName(name)
    out.append(if (asProperty) ": " else "(): ")
    out.append(typeDescriptor.type.getShortText(classContextNamespace))
    if (typeDescriptor.isNullableType) {
      out.append('?')
    }
  }
}