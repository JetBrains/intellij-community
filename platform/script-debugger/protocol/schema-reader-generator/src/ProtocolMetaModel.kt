// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jsonProtocol

import org.jetbrains.io.JsonReaderEx

const val STRING_TYPE: String = "string"
const val INTEGER_TYPE: String = "integer"
const val NUMBER_TYPE: String = "number"
const val BOOLEAN_TYPE: String = "boolean"
public const val OBJECT_TYPE: String = "object"
const val ARRAY_TYPE: String = "array"
const val UNKNOWN_TYPE: String = "unknown"
const val ANY_TYPE: String = "any"

interface ItemDescriptor {
  val description: String?

  val type: String?

  val enum: List<String>?

  val items: ProtocolMetaModel.ArrayItemType?

  interface Named : Referenceable {
    fun name(): String

    val shortName: String?

    val optional: Boolean
  }

  interface Referenceable : ItemDescriptor {
    @ProtocolName("\$ref")
    val ref: String?
  }

  interface Type : ItemDescriptor {
    val properties: List<ProtocolMetaModel.ObjectProperty>?
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
    val version: Version?

    fun domains(): List<Domain>
  }

  interface Version {
    fun major(): String
    fun minor(): String
  }

  interface Domain {
    fun domain(): String

    val types: List<StandaloneType>?

    fun commands(): List<Command>

    val events: List<Event>?

    val description: String?

    val hidden: Boolean

    val experimental: Boolean
  }

  interface Command {
    fun name(): String

    val parameters: List<Parameter>?

    val returns: List<Parameter>?

    val description: String?

    val hidden: Boolean

    val async: Boolean
  }

  interface Parameter : ItemDescriptor.Named {
    val hidden: Boolean

    @JsonField(allowAnyPrimitiveValue = true)
    val default: String?
  }

  interface Event {
    fun name(): String

    val parameters: List<Parameter>?

    val description: String?

    val hidden: Boolean

    val optionalData: Boolean
  }

  @JsonType
  interface StandaloneType : ItemDescriptor.Type {
    fun id(): String

    val hidden: Boolean
  }

  interface ArrayItemType : ItemDescriptor.Type, ItemDescriptor.Referenceable {
    val optional: Boolean
  }

  interface ObjectProperty : ItemDescriptor.Named {
    override fun name(): String

    val hidden: Boolean
  }
}