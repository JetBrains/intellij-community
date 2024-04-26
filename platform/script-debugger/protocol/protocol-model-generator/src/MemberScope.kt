package org.jetbrains.protocolModelGenerator

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.protocolReader.appendEnums

/**
 * Member scope is used to generate additional types that are used only from method.
 * These types will be named after this method.
 */
internal open class MemberScope(private val classScope: ClassScope, protected val memberName: String) : ResolveAndGenerateScope {
  override fun <T : ItemDescriptor> resolveType(typedObject: T) = classScope.generator.generator.resolveType(typedObject, this)

  private val commonEnumNames = listOf("Type", "Format")

  fun generateEnum(description: String?, enumConstants: List<String>): BoxableType {
    var enumName = capitalizeFirstChar(memberName)
    if (getTypeDirection() == TypeData.Direction.OUTPUT) {
      // Enums used as a parameter type (Direction.OUTPUT) do not have a separate type in the protocol.
      // Therefore, during generation, collisions sometimes occur (i.e., the parameter name 'type' is used very often).
      // In such cases, we prepend the enum name with the name of the method.
      // Although this heuristic is not perfect, it allows us to reduce the number of enumerations that will receive ugly names.
      val commonName = commonEnumNames.firstOrNull { StringUtil.equalsIgnoreCase(enumName, it) }
      if (commonName != null) {
        enumName = classScope.classContextNamespace.lastComponent + enumName
      }
    }
    else if (StringUtil.equalsIgnoreCase(enumName, "TYPE") &&
        classScope.classContextNamespace.lastComponent.endsWith("EventData") ) {
      // to avoid same name with companion object TYPE
      enumName = "EventType"
    }
    val namePath = NamePath(enumName, classScope.classContextNamespace)
    var type = classScope.generator.generator.nestedTypeMap.get(namePath)
    if (type == null) {
      type = StandaloneType(namePath, "writeEnum")
      classScope.generator.generator.nestedTypeMap.put(namePath, type)
      classScope.addMember { out ->
        out.newLine().doc(description)
        appendEnums(enumConstants, enumName, classScope.typeDirection == TypeData.Direction.INPUT, out)
      }
    }
    return type
  }

  override fun getDomainName() = classScope.generator.domain.domain()

  override fun getTypeDirection() = classScope.typeDirection
}
