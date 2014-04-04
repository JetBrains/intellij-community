package org.jetbrains.protocolReader;

import org.jetbrains.jsonProtocol.ProtocolMetaModel;

import java.util.List;

interface TypeVisitor<R> {
  R visitRef(String refName);

  R visitBoolean();

  R visitEnum(List<String> enumConstants);

  R visitString();

  R visitInteger();

  R visitNumber();

  R visitArray(ProtocolMetaModel.ArrayItemType items);

  R visitObject(List<ProtocolMetaModel.ObjectProperty> properties);

  R visitMap();

  R visitUnknown();
}