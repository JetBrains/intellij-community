// Generated source
package org.jetbrains.jsonProtocol

import org.jetbrains.jsonProtocol.*

import org.jetbrains.io.JsonReaderEx

import org.jetbrains.jsonProtocol.JsonReaders.*

internal class ProtocolSchemaReaderImpl : org.jetbrains.jsonProtocol.ProtocolSchemaReader {
  override fun parseRoot(reader: org.jetbrains.io.JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.Root = M0(reader,  null)

  private class M0(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.Root {
    override var version: org.jetbrains.jsonProtocol.ProtocolMetaModel.Version? = null
    private var _domains: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain>? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "version" -> version = M1(reader, null)
          "domains" -> _domains = readObjectArray(reader, FM2())
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun domains() = _domains!!

    override fun equals(other: Any?): Boolean = other is M0 && version == other.version && _domains == other._domains
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
    override var description: String? = null
    override var events: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Event>? = null
    override var hidden = false
    override var types: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType>? = null
    private var _commands: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Command>? = null
    private var _domain: String? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "description" -> description = reader.nextString()
          "events" -> events = readObjectArray(reader, FM5())
          "hidden" -> hidden = reader.nextBoolean()
          "types" -> types = readObjectArray(reader, FM6())
          "commands" -> _commands = readObjectArray(reader, FM3())
          "domain" -> _domain = reader.nextString()
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun commands() = _commands!!

    override fun domain() = _domain!!

    override fun equals(other: Any?): Boolean = other is M2 && hidden == other.hidden && description == other.description && _domain == other._domain && events == other.events && types == other.types && _commands == other._commands
  }

  private class M3(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.Command {
    override var async = false
    override var description: String? = null
    override var hidden = false
    override var parameters: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter>? = null
    override var returns: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter>? = null
    private var _name: String? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "async" -> async = reader.nextBoolean()
          "description" -> description = reader.nextString()
          "hidden" -> hidden = reader.nextBoolean()
          "parameters" -> parameters = readObjectArray(reader, FM4())
          "returns" -> returns = readObjectArray(reader, FM4())
          "name" -> _name = reader.nextString()
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun name() = _name!!

    override fun equals(other: Any?): Boolean = other is M3 && async == other.async && hidden == other.hidden && description == other.description && _name == other._name && parameters == other.parameters && returns == other.returns
  }

  private class M4(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter {
    override var default: String? = null
    override var hidden = false
    override var optional = false
    override var shortName: String? = null
    private var _name: String? = null
    override var ref: String? = null
    override var description: String? = null
    override var enum: List<String>? = null
    override var items: org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType? = null
    override var type: String? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "default" -> default = reader.nextString()
          "hidden" -> hidden = reader.nextBoolean()
          "optional" -> optional = reader.nextBoolean()
          "shortName" -> shortName = reader.nextString()
          "name" -> _name = reader.nextString()
          "\$ref" -> ref = reader.nextString()
          "description" -> description = reader.nextString()
          "enum" -> enum = nextList(reader)
          "items" -> items = M7(reader, null)
          "type" -> type = reader.nextString()
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun name() = _name!!

    override fun equals(other: Any?): Boolean = other is M4 && hidden == other.hidden && optional == other.optional && default == other.default && shortName == other.shortName && _name == other._name && ref == other.ref && description == other.description && type == other.type && enum == other.enum && items == other.items
  }

  private class M5(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.Event {
    override var description: String? = null
    override var hidden = false
    override var optionalData = false
    override var parameters: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter>? = null
    private var _name: String? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "description" -> description = reader.nextString()
          "hidden" -> hidden = reader.nextBoolean()
          "optionalData" -> optionalData = reader.nextBoolean()
          "parameters" -> parameters = readObjectArray(reader, FM4())
          "name" -> _name = reader.nextString()
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun name() = _name!!

    override fun equals(other: Any?): Boolean = other is M5 && hidden == other.hidden && optionalData == other.optionalData && description == other.description && _name == other._name && parameters == other.parameters
  }

  private class M6(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType {
    override var hidden = false
    private var _id: String? = null
    override var properties: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty>? = null
    override var description: String? = null
    override var enum: List<String>? = null
    override var items: org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType? = null
    override var type: String? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "hidden" -> hidden = reader.nextBoolean()
          "id" -> _id = reader.nextString()
          "properties" -> properties = readObjectArray(reader, FM8())
          "description" -> description = reader.nextString()
          "enum" -> enum = nextList(reader)
          "items" -> items = M7(reader, null)
          "type" -> type = reader.nextString()
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun id() = _id!!

    override fun equals(other: Any?): Boolean = other is M6 && hidden == other.hidden && _id == other._id && description == other.description && type == other.type && properties == other.properties && enum == other.enum && items == other.items
  }

  private class M7(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType {
    override var optional = false
    override var properties: List<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty>? = null
    override var description: String? = null
    override var enum: List<String>? = null
    override var items: org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType? = null
    override var type: String? = null
    override var ref: String? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "optional" -> optional = reader.nextBoolean()
          "properties" -> properties = readObjectArray(reader, FM8())
          "description" -> description = reader.nextString()
          "enum" -> enum = nextList(reader)
          "items" -> items = M7(reader, null)
          "type" -> type = reader.nextString()
          "\$ref" -> ref = reader.nextString()
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun equals(other: Any?): Boolean = other is M7 && optional == other.optional && description == other.description && type == other.type && ref == other.ref && properties == other.properties && enum == other.enum && items == other.items
  }

  private class M8(reader: JsonReaderEx, preReadName: String?) : org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty {
    override var hidden = false
    private var _name: String? = null
    override var optional = false
    override var shortName: String? = null
    override var ref: String? = null
    override var description: String? = null
    override var enum: List<String>? = null
    override var items: org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType? = null
    override var type: String? = null

    init {
      var name = preReadName
      if (name == null && reader.hasNext() && reader.beginObject().hasNext()) {
        name = reader.nextName()
      }

      loop@ while (name != null) {
        when (name) {
          "hidden" -> hidden = reader.nextBoolean()
          "name" -> _name = reader.nextString()
          "optional" -> optional = reader.nextBoolean()
          "shortName" -> shortName = reader.nextString()
          "\$ref" -> ref = reader.nextString()
          "description" -> description = reader.nextString()
          "enum" -> enum = nextList(reader)
          "items" -> items = M7(reader, null)
          "type" -> type = reader.nextString()
          else -> reader.skipValue()
        }
        name = reader.nextNameOrNull()
      }

      reader.endObject()
    }

    override fun name() = _name!!

    override fun equals(other: Any?): Boolean = other is M8 && hidden == other.hidden && optional == other.optional && _name == other._name && shortName == other.shortName && ref == other.ref && description == other.description && type == other.type && enum == other.enum && items == other.items
  }

  private class FM2 : ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain>() {
    override fun read(reader: JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain = M2(reader, null)
  }

  private class FM5 : ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Event>() {
    override fun read(reader: JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.Event = M5(reader, null)
  }

  private class FM6 : ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType>() {
    override fun read(reader: JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType = M6(reader, null)
  }

  private class FM3 : ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Command>() {
    override fun read(reader: JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.Command = M3(reader, null)
  }

  private class FM4 : ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter>() {
    override fun read(reader: JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter = M4(reader, null)
  }

  private class FM8 : ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty>() {
    override fun read(reader: JsonReaderEx): org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty = M8(reader, null)
  }
}