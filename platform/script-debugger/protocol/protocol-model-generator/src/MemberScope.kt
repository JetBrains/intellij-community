package org.jetbrains.protocolReader

import org.jetbrains.jsonProtocol.ItemDescriptor

/**
 * Member scope is used to generate additional types that are used only from method.
 * These types will be named after this method.
 */
abstract class MemberScope(private val classScope: ClassScope, protected val memberName: String) : ResolveAndGenerateScope {
  override fun <T : ItemDescriptor> resolveType(typedObject: T): TypeDescriptor {
    return classScope.generator.generator.resolveType(typedObject, this)
  }

  public abstract fun generateEnum(description: String?, enumConstants: List<String>): BoxableType

  override fun getDomainName() = classScope.generator.domain.domain()

  override fun getTypeDirection() = classScope.getTypeDirection()
}
