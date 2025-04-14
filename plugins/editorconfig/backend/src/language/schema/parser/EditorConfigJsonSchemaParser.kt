// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.Stack
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigConstantDescriptor
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.CONST
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.DECLARATION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.LIST
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.NUMBER
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.OPTION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.PAIR
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.QUALIFIED
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.REFERENCE
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.STRING
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.TEXT
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.TYPE
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.UNION
import org.editorconfig.language.schema.parser.handlers.EditorConfigDescriptorParseHandlerBase
import org.editorconfig.language.schema.parser.handlers.impl.*

class EditorConfigJsonSchemaParser(private val logger: Logger) {
  private val handlers: Map<String, EditorConfigDescriptorParseHandlerBase>
  private val typeAliases = mutableMapOf<String, JsonObject>()
  private val forbiddenTypes = Stack<String>()

  fun registerTypeAlias(alias: String, type: JsonObject) {
    val presentAlias = typeAliases[alias]
    if (presentAlias == type) return

    if (presentAlias != null) {
      warn("Attempted to register type alias $alias twice")
      return
    }

    val presentHandler = handlers[alias]
    if (presentHandler != null) {
      warn("Attempted to register type alias $alias that conflicts existing parse handler")
      return
    }

    typeAliases[alias] = type
  }

  init {
    val handlers = mutableMapOf<String, EditorConfigDescriptorParseHandlerBase>()
    fun registerParseHandler(pair: Pair<String, EditorConfigDescriptorParseHandlerBase>): EditorConfigJsonSchemaParser {
      if (handlers[pair.first] != null) {
        throw IllegalStateException("Handler with key ${pair.first} has already been registered")
      }
      handlers[pair.first] = pair.second
      return this
    }

    val standardTypeParseHandler = EditorConfigStandardTypeDescriptorParseHandler()
    registerParseHandler(NUMBER to standardTypeParseHandler)
    registerParseHandler(STRING to standardTypeParseHandler)
    registerParseHandler(TEXT to standardTypeParseHandler)

    registerParseHandler(DECLARATION to EditorConfigDeclarationDescriptorParseHandler())
    registerParseHandler(REFERENCE to EditorConfigReferenceDescriptorParseHandler())
    registerParseHandler(LIST to EditorConfigListDescriptorParseHandler())
    registerParseHandler(UNION to EditorConfigUnionDescriptorParseHandler())
    registerParseHandler(CONST to EditorConfigConstantDescriptorParseHandler())
    registerParseHandler(PAIR to EditorConfigPairDescriptorParseHandler())
    registerParseHandler(OPTION to EditorConfigOptionDescriptorParseHandler())
    registerParseHandler(QUALIFIED to EditorConfigQualifiedOptionKeyDescriptorParseHandler())

    this.handlers = handlers
  }

  fun parse(element: JsonElement): EditorConfigDescriptor {
    if (element.isJsonPrimitive) {
      // Handle standalone constant
      return EditorConfigConstantDescriptor(element.asString, null, null)
    }

    if (!element.isJsonObject) {
      throw EditorConfigJsonSchemaException(element)
    }

    val jsonObject = element.asJsonObject
    val type = getTypeName(jsonObject)

    if (forbiddenTypes.contains(type)) {
      throw EditorConfigJsonSchemaException(jsonObject)
    }

    val handler = handlers[type]
    if (handler != null) {
      try {
        handler.forbiddenChildren.forEach(forbiddenTypes::push)
        return handler.handle(jsonObject, this)
      }
      finally {
        repeat(handler.forbiddenChildren.size) { forbiddenTypes.tryPop() }
      }
    }

    val alias = typeAliases[type] ?: throw EditorConfigJsonSchemaException(jsonObject)
    return parse(alias)
  }

  private fun getTypeName(jsonObject: JsonObject): String {
    val type = jsonObject[TYPE]
    if (type == null || !type.isJsonPrimitive) {
      throw EditorConfigJsonSchemaException(jsonObject)
    }
    return type.asString
  }

  fun warn(message: String) {
    logger.warn(message)
  }
}
