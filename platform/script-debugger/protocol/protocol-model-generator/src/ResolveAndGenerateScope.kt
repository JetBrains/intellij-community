package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.jsonProtocol.ProtocolMetaModel

internal interface ResolveAndGenerateScope {
  fun getDomainName(): String
  fun getTypeDirection(): TypeData.Direction

  fun <T : ItemDescriptor> resolveType(typedObject: T): TypeDescriptor = throw UnsupportedOperationException()

  open fun generateNestedObject(description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?): BoxableType = throw UnsupportedOperationException()
}
