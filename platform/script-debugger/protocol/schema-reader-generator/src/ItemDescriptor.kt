package org.jetbrains.jsonProtocol

public interface ItemDescriptor {
  public fun description(): String?

  public fun type(): String

  public fun getEnum(): List<String>?

  public fun items(): ProtocolMetaModel.ArrayItemType

  public interface Named : Referenceable {
    public fun name(): String

    JsonOptionalField
    public fun shortName(): String?

    public fun optional(): Boolean
  }

  public interface Referenceable : ItemDescriptor {
    public fun ref(): String
  }

  public interface Type : ItemDescriptor {
    public fun properties(): List<ProtocolMetaModel.ObjectProperty>?
  }
}