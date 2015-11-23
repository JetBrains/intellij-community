package org.jetbrains.jsonProtocol

import org.jetbrains.io.JsonReaderEx

val STRING_TYPE: String = "string"
val INTEGER_TYPE: String = "integer"
val NUMBER_TYPE: String = "number"
val BOOLEAN_TYPE: String = "boolean"
public val OBJECT_TYPE: String = "object"
val ARRAY_TYPE: String = "array"
val UNKNOWN_TYPE: String = "unknown"
val ANY_TYPE: String = "any"

interface ItemDescriptor {
  fun description(): String?

  fun type(): String?

  fun getEnum(): List<String>?

  fun items(): ProtocolMetaModel.ArrayItemType?

  interface Named : Referenceable {
    fun name(): String

    @JsonOptionalField
    fun shortName(): String?

    fun optional(): Boolean
  }

  interface Referenceable : ItemDescriptor {
    fun ref(): String?
  }

  interface Type : ItemDescriptor {
    fun properties(): List<ProtocolMetaModel.ObjectProperty>?
  }
}

interface ProtocolSchemaReader {
  @JsonParseMethod
  fun parseRoot(reader: JsonReaderEx): ProtocolMetaModel.Root
}

/**
 * Defines schema of WIP metamodel defined in http://svn.webkit.org/repository/webkit/trunk/Source/WebCore/inspector/Inspector.json
 */
interface ProtocolMetaModel {
  @JsonType
  interface Root {
    @JsonOptionalField
    fun version(): Version?

    fun domains(): List<Domain>
  }

  @JsonType
  interface Version {
    fun major(): String
    fun minor(): String
  }

  @JsonType
  interface Domain {
    fun domain(): String

    @JsonOptionalField
    fun types(): List<StandaloneType>?

    fun commands(): List<Command>

    @JsonOptionalField
    fun events(): List<Event>?

    @JsonOptionalField
    fun description(): String?

    @JsonOptionalField
    fun hidden(): Boolean
  }

  @JsonType
  interface Command {
    fun name(): String

    @JsonOptionalField
    fun parameters(): List<Parameter>?

    @JsonOptionalField
    fun returns(): List<Parameter>?

    @JsonOptionalField
    fun description(): String?

    @JsonOptionalField
    fun hidden(): Boolean

    @JsonOptionalField
    fun async(): Boolean
  }

  @JsonType
  interface Parameter : ItemDescriptor.Named {
    override fun name(): String

    @JsonOptionalField
    override fun shortName(): String?

    @JsonOptionalField
    override fun type(): String?

    @JsonOptionalField
    override fun items(): ArrayItemType?

    @JsonField(name = "enum", optional = true)
    override fun getEnum(): List<String>?

    @JsonField(name = "\$ref", optional = true)
    override fun ref(): String?

    @JsonOptionalField
    override fun optional(): Boolean

    @JsonOptionalField
    override fun description(): String?

    @JsonOptionalField
    public fun hidden(): Boolean
  }

  @JsonType
  interface Event {
    fun name(): String

    @JsonOptionalField
    fun parameters(): List<Parameter>?

    @JsonOptionalField
    fun description(): String?

    @JsonOptionalField
    fun hidden(): Boolean
  }

  @JsonType
  interface StandaloneType : ItemDescriptor.Type {
    fun id(): String

    @JsonOptionalField
    override fun description(): String?

    override fun type(): String

    @JsonOptionalField
    public fun hidden(): Boolean

    @JsonOptionalField
    override fun properties(): List<ObjectProperty>?

    @JsonField(name = "enum", optional = true)
    override fun getEnum(): List<String>?

    @JsonOptionalField
    override fun items(): ArrayItemType?
  }


  @JsonType
  interface ArrayItemType : ItemDescriptor.Type, ItemDescriptor.Referenceable {
    @JsonOptionalField
    override fun description(): String?

    @JsonOptionalField
    public fun optional(): Boolean

    @JsonOptionalField
    override fun type(): String?

    @JsonOptionalField
    override fun items(): ArrayItemType?

    @JsonField(name = "\$ref", optional = true)
    override fun ref(): String?

    @JsonField(name = "enum", optional = true)
    override fun getEnum(): List<String>?

    @JsonOptionalField
    override fun properties(): List<ObjectProperty>?
  }

  @JsonType
  interface ObjectProperty : ItemDescriptor.Named {
    override fun name(): String

    @JsonOptionalField
    override fun shortName(): String?

    @JsonOptionalField
    override fun description(): String?

    @JsonOptionalField
    override fun optional(): Boolean

    @JsonOptionalField
    override fun type(): String?

    @JsonOptionalField
    override fun items(): ArrayItemType?

    @JsonField(name = "\$ref", optional = true)
    override fun ref(): String?

    @JsonField(name = "enum", optional = true)
    override fun getEnum(): List<String>?

    @JsonOptionalField
    fun hidden(): Boolean
  }
}