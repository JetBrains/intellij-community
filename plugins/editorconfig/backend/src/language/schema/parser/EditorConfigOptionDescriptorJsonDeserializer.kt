// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser

import com.google.gson.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import java.lang.reflect.Type

class EditorConfigOptionDescriptorJsonDeserializer(logger: Logger) : JsonDeserializer<EditorConfigOptionDescriptor> {
  val parser: EditorConfigJsonSchemaParser = EditorConfigJsonSchemaParser(logger)

  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): EditorConfigOptionDescriptor? = try {
    parser.parse(json) as? EditorConfigOptionDescriptor ?: throw EditorConfigJsonSchemaException(json)
  }
  catch (ex: EditorConfigJsonSchemaException) {
    parser.warn("Found illegal descriptor: ${ex.element}")
    null
  }

  companion object {
    fun buildGson(logger: Logger = logger<EditorConfigJsonSchemaParser>()): Gson =
      GsonBuilder()
        .registerTypeAdapter(
          EditorConfigOptionDescriptor::class.java,
          EditorConfigOptionDescriptorJsonDeserializer(logger))
        .create()
  }
}
