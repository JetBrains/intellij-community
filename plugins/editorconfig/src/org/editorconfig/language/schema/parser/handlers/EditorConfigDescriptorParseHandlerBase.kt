// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser.handlers

import com.google.gson.JsonObject
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.DEPRECATION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.DOCUMENTATION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.TYPE_ALIAS
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaException
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaParser

abstract class EditorConfigDescriptorParseHandlerBase {
  protected abstract val requiredKeys: List<String>
  protected open val optionalKeys: List<String> = listOf(DOCUMENTATION, TYPE_ALIAS, DEPRECATION)

  open val forbiddenChildren = emptyList<String>()

  protected abstract fun doHandle(jsonObject: JsonObject, parser: EditorConfigJsonSchemaParser): EditorConfigDescriptor

  fun handle(jsonObject: JsonObject, parser: EditorConfigJsonSchemaParser): EditorConfigDescriptor {
    assertContents(jsonObject, parser)
    val result = doHandle(jsonObject, parser)
    val alias = tryGetString(jsonObject, TYPE_ALIAS)
    if (alias != null) {
      parser.registerTypeAlias(alias, jsonObject)
    }

    return result
  }

  private fun assertContents(jsonObject: JsonObject, parser: EditorConfigJsonSchemaParser) {
    val keys = jsonObject.keySet()
    if (!keys.containsAll(requiredKeys)) {
      throw EditorConfigJsonSchemaException(jsonObject)
    }
    if (!keys.all { it in requiredKeys || it in optionalKeys }) {
      parser.warn("Unexpected option value descriptor key in $keys")
    }
  }

  private fun tryGetElement(jsonObject: JsonObject, key: String) =
    if (jsonObject.has(key)) jsonObject[key]
    else null

  protected fun tryGetString(jsonObject: JsonObject, key: String) = try {
    tryGetElement(jsonObject, key)?.asString
  }
  catch (ex: ClassCastException) {
    null
  }
  catch (ex: IllegalStateException) {
    null
  }

  protected fun tryGetInt(jsonObject: JsonObject, key: String) = try {
    tryGetElement(jsonObject, key)?.asInt
  }
  catch (ex: ClassCastException) {
    null
  }
  catch (ex: IllegalStateException) {
    null
  }

  protected fun tryGetBoolean(jsonObject: JsonObject, key: String) = try {
    tryGetElement(jsonObject, key)?.asBoolean
  }
  catch (ex: ClassCastException) {
    null
  }
  catch (ex: IllegalStateException) {
    null
  }
}
