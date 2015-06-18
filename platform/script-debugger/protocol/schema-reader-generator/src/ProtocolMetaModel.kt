package org.jetbrains.jsonProtocol

public val STRING_TYPE: String = "string"
public val INTEGER_TYPE: String = "integer"
public val NUMBER_TYPE: String = "number"
public val BOOLEAN_TYPE: String = "boolean"
public val OBJECT_TYPE: String = "object"
public val ARRAY_TYPE: String = "array"
public val UNKNOWN_TYPE: String = "unknown"
public val ANY_TYPE: String = "any"

/**
 * Defines schema of WIP metamodel defined in http://svn.webkit.org/repository/webkit/trunk/Source/WebCore/inspector/Inspector.json
 */
public interface ProtocolMetaModel {
  JsonType
  public interface Root {
    JsonOptionalField
    public fun version(): Version

    public fun domains(): List<Domain>
  }

  JsonType
  public interface Version {
    public fun major(): String
    public fun minor(): String
  }

  JsonType
  public interface Domain {
    public fun domain(): String

    JsonOptionalField
    public fun types(): List<StandaloneType>?

    public fun commands(): List<Command>

    JsonOptionalField
    public fun events(): List<Event>?

    JsonOptionalField
    public fun description(): String

    JsonOptionalField
    public fun hidden(): Boolean
  }

  JsonType
  public interface Command {
    public fun name(): String

    JsonOptionalField
    public fun parameters(): List<Parameter>?

    JsonOptionalField
    public fun returns(): List<Parameter>?

    JsonOptionalField
    public fun description(): String

    JsonOptionalField
    public fun hidden(): Boolean

    JsonOptionalField
    public fun async(): Boolean
  }

  JsonType
  public interface Parameter : ItemDescriptor.Named {
    override fun name(): String

    JsonOptionalField
    override fun shortName(): String

    JsonOptionalField
    override fun type(): String

    JsonOptionalField
    override fun items(): ArrayItemType

    JsonField(name = "enum", optional = true)
    override fun getEnum(): List<String>

    JsonField(name = "\$ref", optional = true)
    override fun ref(): String

    JsonOptionalField
    override fun optional(): Boolean

    JsonOptionalField
    override fun description(): String

    JsonOptionalField
    public fun hidden(): Boolean
  }

  JsonType
  public interface Event {
    public fun name(): String

    JsonOptionalField
    public fun parameters(): List<Parameter>

    JsonOptionalField
    public fun description(): String

    JsonOptionalField
    public fun hidden(): Boolean
  }

  JsonType
  public interface StandaloneType : ItemDescriptor.Type {
    public fun id(): String

    JsonOptionalField
    override fun description(): String?

    override fun type(): String

    JsonOptionalField
    public fun hidden(): Boolean

    JsonOptionalField
    override fun properties(): List<ObjectProperty>

    JsonField(name = "enum", optional = true)
    override fun getEnum(): List<String>

    JsonOptionalField
    override fun items(): ArrayItemType
  }


  JsonType
  public interface ArrayItemType : ItemDescriptor.Type, ItemDescriptor.Referenceable {
    JsonOptionalField
    override fun description(): String

    JsonOptionalField
    public fun optional(): Boolean

    JsonOptionalField
    override fun type(): String

    JsonOptionalField
    override fun items(): ArrayItemType

    JsonField(name = "\$ref", optional = true)
    override fun ref(): String

    JsonField(name = "enum", optional = true)
    override fun getEnum(): List<String>

    JsonOptionalField
    override fun properties(): List<ObjectProperty>
  }

  JsonType
  public interface ObjectProperty : ItemDescriptor.Named {
    override fun name(): String

    JsonOptionalField
    override fun shortName(): String

    JsonOptionalField
    override fun description(): String

    JsonOptionalField
    override fun optional(): Boolean

    JsonOptionalField
    override fun type(): String

    JsonOptionalField
    override fun items(): ArrayItemType

    JsonField(name = "\$ref", optional = true)
    override fun ref(): String

    JsonField(name = "enum", optional = true)
    override fun getEnum(): List<String>

    JsonOptionalField
    public fun hidden(): Boolean
  }
}