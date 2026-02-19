// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.protocolReader

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

const val TYPE_FACTORY_NAME_PREFIX: Char = 'F'

const val READER_NAME: String = "reader"
const val PENDING_INPUT_READER_NAME: String = "inputReader"

const val JSON_READER_CLASS_NAME: String = "JsonReaderEx"
val JSON_READER_PARAMETER_DEF: String = "$READER_NAME: $JSON_READER_CLASS_NAME"

/**
 * Generate Java type name of the passed type. Type may be parameterized.
 */
internal fun writeJavaTypeName(arg: Type, out: TextOutput) {
  when (arg) {
    is Class<*> -> {
      val name = arg.canonicalName
      out.append(
        when (name) {
          "java.util.List" -> "List"
          "java.lang.String" -> "String?"
          else -> name
        }
      )
    }
    is ParameterizedType -> {
      writeJavaTypeName(arg.rawType, out)
      out.append('<')
      val params = arg.actualTypeArguments
      for (i in params.indices) {
        if (i != 0) {
          out.comma()
        }
        writeJavaTypeName(params[i], out)
      }
      out.append('>')
    }
    is WildcardType -> {
      val upperBounds = arg.upperBounds!!
      if (upperBounds.size != 1) {
        throw RuntimeException()
      }
      out.append("? extends ")
      writeJavaTypeName(upperBounds.first(), out)
    }
    else -> {
      out.append(arg.toString())
    }
  }
}