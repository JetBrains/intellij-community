package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.protocolReader.appendEnums

/**
 * Member scope is used to generate additional types that are used only from method.
 * These types will be named after this method.
 */
internal open class MemberScope(private val classScope: ClassScope, protected val memberName: String) : ResolveAndGenerateScope {
  override fun <T : ItemDescriptor> resolveType(typedObject: T) = classScope.generator.generator.resolveType(typedObject, this)

  fun generateEnum(description: String?, enumConstants: List<String>): BoxableType {
    val enumName = capitalizeFirstChar(memberName)
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
