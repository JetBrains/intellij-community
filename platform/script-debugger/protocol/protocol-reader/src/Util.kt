package org.jetbrains.protocolReader

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

public val TYPE_NAME_PREFIX: Char = 'M'
public val TYPE_FACTORY_NAME_POSTFIX: Char = 'F'

public val READER_NAME: String = "reader"
public val PENDING_INPUT_READER_NAME: String = "inputReader"

public val BASE_VALUE_PREFIX: String = "baseMessage"

public val JSON_READER_CLASS_NAME: String = "org.jetbrains.io.JsonReaderEx"
public val JSON_READER_PARAMETER_DEF: String = JSON_READER_CLASS_NAME + ' ' + READER_NAME

/**
 * Generate Java type name of the passed type. Type may be parameterized.
 */
fun writeJavaTypeName(arg: Type, out: TextOutput) {
  if (arg is Class<*>) {
    out.append(arg.getCanonicalName())
  }
  else if (arg is ParameterizedType) {
    writeJavaTypeName(arg.getRawType(), out)
    out.append('<')
    val params = arg.getActualTypeArguments()
    for (i in params.indices) {
      if (i != 0) {
        out.comma()
      }
      writeJavaTypeName(params[i], out)
    }
    out.append('>')
  }
  else if (arg is WildcardType) {
    val upperBounds = arg.getUpperBounds()
    if (upperBounds == null) {
      throw RuntimeException()
    }
    if (upperBounds.size() != 1) {
      throw RuntimeException()
    }
    out.append("? extends ")
    writeJavaTypeName(upperBounds[0], out)
  }
  else {
    out.append(arg.toString())
  }
}