package org.jetbrains.protocolReader

trait ResolveAndGenerateScope {
  public fun getDomainName(): String
  public fun getTypeDirection(): TypeData.Direction

  public fun <T : ItemDescriptor> resolveType(typedObject: T): TypeDescriptor

  public fun generateNestedObject(description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?): BoxableType
}
