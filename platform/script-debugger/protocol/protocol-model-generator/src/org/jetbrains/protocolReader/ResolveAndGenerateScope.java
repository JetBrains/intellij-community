package org.jetbrains.protocolReader;

import org.jetbrains.jsonProtocol.ItemDescriptor;
import org.jetbrains.jsonProtocol.ProtocolMetaModel;

import java.util.List;

interface ResolveAndGenerateScope {
  String getDomainName();
  TypeData.Direction getTypeDirection();

  <T extends ItemDescriptor> TypeDescriptor resolveType(T typedObject);

  BoxableType generateNestedObject(String description,
                                   List<ProtocolMetaModel.ObjectProperty> properties);
}
