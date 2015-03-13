package org.jetbrains.jsonProtocol

public trait ItemDescriptor {
  public fun description(): String?

  public fun type(): String

  public fun getEnum(): List<String>?

  public fun items(): ProtocolMetaModel.ArrayItemType

  public trait Named : Referenceable {
    public fun name(): String

    JsonOptionalField
    public fun shortName(): String?

    public fun optional(): Boolean
  }

  public trait Referenceable : ItemDescriptor {
    public fun ref(): String
  }

  public trait Type : ItemDescriptor {
    public fun properties(): List<ProtocolMetaModel.ObjectProperty>?
  }
}