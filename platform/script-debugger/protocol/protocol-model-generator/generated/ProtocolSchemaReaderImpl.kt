// Generated source
package org.jetbrains.jsonProtocol

import org.jetbrains.jsonProtocol.*

import org.jetbrains.io.JsonReaderEx

import org.jetbrains.jsonProtocol.JsonReaders.*

internal class ProtocolSchemaReaderImpl : org.jetbrains.jsonProtocol.ProtocolSchemaReader {
  override fun parseRoot(reader: org.jetbrains.io.JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.Root = M0(reader,  null)

  private class M0(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.Root {
    private var _domains: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain>? = null
    private var _version: org.jetbrains.jsonProtocol.ProtocolMetaModel.Version? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "domains" -> _domains = readObjectArray(reader, FM2())
          "version" -> _version = M1(reader, null)
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun domains() = _domains!!

    override fun version() = _version

    override fun equals(other: Any?): Boolean = other is M0 && _domains == other._domains && _version == other._version
  }

  private class M1(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.Version {
    private var _major: String? = null
    private var _minor: String? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "major" -> _major = reader.nextString()
          "minor" -> _minor = reader.nextString()
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun major() = _major!!

    override fun minor() = _minor!!

    override fun equals(other: Any?): Boolean = other is M1 && _major == other._major && _minor == other._minor
  }

  private class M2(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain {
    private var _commands: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Command>? = null
    private var _description: String? = null
    private var _domain: String? = null
    private var _events: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Event>? = null
    private var _hidden = false
    private var _types: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType>? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "commands" -> _commands = readObjectArray(reader, FM3())
          "description" -> _description = reader.nextNullableString()
          "domain" -> _domain = reader.nextString()
          "events" -> _events = readObjectArray(reader, FM5())
          "hidden" -> _hidden = reader.nextBoolean()
          "types" -> _types = readObjectArray(reader, FM6())
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun commands() = _commands!!

    override fun description() = _description

    override fun domain() = _domain!!

    override fun events() = _events

    override fun hidden() = _hidden

    override fun types() = _types

    override fun equals(other: Any?): Boolean = other is M2 && _hidden == other._hidden && _description == other._description && _domain == other._domain && _commands == other._commands && _events == other._events && _types == other._types
  }

  private class M3(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.Command {
    private var _async = false
    private var _description: String? = null
    private var _hidden = false
    private var _name: String? = null
    private var _parameters: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter>? = null
    private var _returns: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter>? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "async" -> _async = reader.nextBoolean()
          "description" -> _description = reader.nextNullableString()
          "hidden" -> _hidden = reader.nextBoolean()
          "name" -> _name = reader.nextString()
          "parameters" -> _parameters = readObjectArray(reader, FM4())
          "returns" -> _returns = readObjectArray(reader, FM4())
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun async() = _async

    override fun description() = _description

    override fun hidden() = _hidden

    override fun name() = _name!!

    override fun parameters() = _parameters

    override fun returns() = _returns

    override fun equals(other: Any?): Boolean = other is M3 && _async == other._async && _hidden == other._hidden && _description == other._description && _name == other._name && _parameters == other._parameters && _returns == other._returns
  }

  private class M4(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter {
    private var _description: String? = null
    private var _getEnum: List<String>? = null
    private var _hidden = false
    private var _items: org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType? = null
    private var _name: String? = null
    private var _optional = false
    private var _ref: String? = null
    private var _shortName: String? = null
    private var _type: String? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "description" -> _description = reader.nextNullableString()
          "enum" -> _getEnum = nextList(reader)
          "hidden" -> _hidden = reader.nextBoolean()
          "items" -> _items = M7(reader, null)
          "name" -> _name = reader.nextString()
          "optional" -> _optional = reader.nextBoolean()
          "\$ref" -> _ref = reader.nextNullableString()
          "shortName" -> _shortName = reader.nextNullableString()
          "type" -> _type = reader.nextNullableString()
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun description() = _description

    override fun getEnum() = _getEnum

    override fun hidden() = _hidden

    override fun items() = _items

    override fun name() = _name!!

    override fun optional() = _optional

    override fun ref() = _ref

    override fun shortName() = _shortName

    override fun type() = _type

    override fun equals(other: Any?): Boolean = other is M4 && _hidden == other._hidden && _optional == other._optional && _description == other._description && _name == other._name && _ref == other._ref && _shortName == other._shortName && _type == other._type && _getEnum == other._getEnum && _items == other._items
  }

  private class M5(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.Event {
    private var _description: String? = null
    private var _hidden = false
    private var _name: String? = null
    private var _optionalData = false
    private var _parameters: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter>? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "description" -> _description = reader.nextNullableString()
          "hidden" -> _hidden = reader.nextBoolean()
          "name" -> _name = reader.nextString()
          "optionalData" -> _optionalData = reader.nextBoolean()
          "parameters" -> _parameters = readObjectArray(reader, FM4())
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun description() = _description

    override fun hidden() = _hidden

    override fun name() = _name!!

    override fun optionalData() = _optionalData

    override fun parameters() = _parameters

    override fun equals(other: Any?): Boolean = other is M5 && _hidden == other._hidden && _optionalData == other._optionalData && _description == other._description && _name == other._name && _parameters == other._parameters
  }

  private class M6(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType {
    private var _description: String? = null
    private var _getEnum: List<String>? = null
    private var _hidden = false
    private var _id: String? = null
    private var _items: org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType? = null
    private var _properties: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty>? = null
    private var _type: String? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "description" -> _description = reader.nextNullableString()
          "enum" -> _getEnum = nextList(reader)
          "hidden" -> _hidden = reader.nextBoolean()
          "id" -> _id = reader.nextString()
          "items" -> _items = M7(reader, null)
          "properties" -> _properties = readObjectArray(reader, FM8())
          "type" -> _type = reader.nextString()
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun description() = _description

    override fun getEnum() = _getEnum

    override fun hidden() = _hidden

    override fun id() = _id!!

    override fun items() = _items

    override fun properties() = _properties

    override fun type() = _type!!

    override fun equals(other: Any?): Boolean = other is M6 && _hidden == other._hidden && _description == other._description && _id == other._id && _type == other._type && _getEnum == other._getEnum && _items == other._items && _properties == other._properties
  }

  private class M7(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType {
    private var _description: String? = null
    private var _getEnum: List<String>? = null
    private var _items: org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType? = null
    private var _optional = false
    private var _properties: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty>? = null
    private var _ref: String? = null
    private var _type: String? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "description" -> _description = reader.nextNullableString()
          "enum" -> _getEnum = nextList(reader)
          "items" -> _items = M7(reader, null)
          "optional" -> _optional = reader.nextBoolean()
          "properties" -> _properties = readObjectArray(reader, FM8())
          "\$ref" -> _ref = reader.nextNullableString()
          "type" -> _type = reader.nextNullableString()
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun description() = _description

    override fun getEnum() = _getEnum

    override fun items() = _items

    override fun optional() = _optional

    override fun properties() = _properties

    override fun ref() = _ref

    override fun type() = _type

    override fun equals(other: Any?): Boolean = other is M7 && _optional == other._optional && _description == other._description && _ref == other._ref && _type == other._type && _getEnum == other._getEnum && _items == other._items && _properties == other._properties
  }

  private class M8(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty {
    private var _description: String? = null
    private var _getEnum: List<String>? = null
    private var _hidden = false
    private var _items: org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType? = null
    private var _name: String? = null
    private var _optional = false
    private var _ref: String? = null
    private var _shortName: String? = null
    private var _type: String? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "description" -> _description = reader.nextNullableString()
          "enum" -> _getEnum = nextList(reader)
          "hidden" -> _hidden = reader.nextBoolean()
          "items" -> _items = M7(reader, null)
          "name" -> _name = reader.nextString()
          "optional" -> _optional = reader.nextBoolean()
          "\$ref" -> _ref = reader.nextNullableString()
          "shortName" -> _shortName = reader.nextNullableString()
          "type" -> _type = reader.nextNullableString()
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun description() = _description

    override fun getEnum() = _getEnum

    override fun hidden() = _hidden

    override fun items() = _items

    override fun name() = _name!!

    override fun optional() = _optional

    override fun ref() = _ref

    override fun shortName() = _shortName

    override fun type() = _type

    override fun equals(other: Any?): Boolean = other is M8 && _hidden == other._hidden && _optional == other._optional && _description == other._description && _name == other._name && _ref == other._ref && _shortName == other._shortName && _type == other._type && _getEnum == other._getEnum && _items == other._items
  }

  private class FM2 : ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain>() {
    override fun read(reader: JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain = M2(reader, null)
  }

  private class FM3 : ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Command>() {
    override fun read(reader: JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.Command = M3(reader, null)
  }

  private class FM5 : ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Event>() {
    override fun read(reader: JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.Event = M5(reader, null)
  }

  private class FM6 : ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType>() {
    override fun read(reader: JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType = M6(reader, null)
  }

  private class FM4 : ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter>() {
    override fun read(reader: JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter = M4(reader, null)
  }

  private class FM8 : ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty>() {
    override fun read(reader: JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty = M8(reader, null)
  }
}