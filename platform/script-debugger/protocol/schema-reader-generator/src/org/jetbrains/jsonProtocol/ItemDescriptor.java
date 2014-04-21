package org.jetbrains.jsonProtocol;

import org.chromium.protocolReader.JsonOptionalField;

import java.util.List;

public interface ItemDescriptor {
  String description();

  String type();

  List<String> getEnum();

  ProtocolMetaModel.ArrayItemType items();

  interface Named extends Referenceable {
    String name();

    @JsonOptionalField
    String shortName();

    boolean optional();
  }

  interface Referenceable extends ItemDescriptor {
    String ref();
  }

  interface Type extends ItemDescriptor {
    List<ProtocolMetaModel.ObjectProperty> properties();
  }
}