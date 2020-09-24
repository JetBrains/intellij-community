// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser.handlers.impl

import com.google.gson.JsonObject
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.*
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.ALLOW_REPETITIONS
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.LIST
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.MIN_LENGTH
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.OPTION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.PAIR
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.TYPE
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.VALUES
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaException
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaParser
import org.editorconfig.language.schema.parser.handlers.EditorConfigDescriptorParseHandlerBase

class EditorConfigListDescriptorParseHandler : EditorConfigDescriptorParseHandlerBase() {
  override val requiredKeys = listOf(TYPE, VALUES)
  override val optionalKeys = super.optionalKeys + listOf(MIN_LENGTH, ALLOW_REPETITIONS)

  override val forbiddenChildren = listOf(PAIR, LIST, OPTION)

  override fun doHandle(jsonObject: JsonObject, parser: EditorConfigJsonSchemaParser): EditorConfigDescriptor {
    val minLength = tryGetInt(jsonObject, MIN_LENGTH) ?: 0
    val allowRepetitions = tryGetBoolean(jsonObject, ALLOW_REPETITIONS) ?: false

    val values = jsonObject[VALUES]
    if (!values.isJsonArray) throw EditorConfigJsonSchemaException(jsonObject)

    val children = values.asJsonArray.map(parser::parse)
    if (!children.all(::isAcceptable)) throw EditorConfigJsonSchemaException(jsonObject)

    val documentation = tryGetString(jsonObject, EditorConfigJsonSchemaConstants.DOCUMENTATION)
    val deprecation = tryGetString(jsonObject, EditorConfigJsonSchemaConstants.DEPRECATION)

    return EditorConfigListDescriptor(minLength, allowRepetitions, children, documentation, deprecation)
  }

  private fun isAcceptable(descriptor: EditorConfigDescriptor) = when (descriptor) {
    is EditorConfigConstantDescriptor -> true
    is EditorConfigNumberDescriptor -> true
    is EditorConfigUnionDescriptor -> true
    is EditorConfigStringDescriptor -> true
    else -> false
  }
}
