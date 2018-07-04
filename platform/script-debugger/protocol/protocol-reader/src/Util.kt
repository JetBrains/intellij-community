package org.jetbrains.protocolReader

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

val TYPE_FACTORY_NAME_PREFIX: Char = 'F'

val READER_NAME: String = "reader"
val PENDING_INPUT_READER_NAME: String = "inputReader"

val JSON_READER_CLASS_NAME: String = "JsonReaderEx"
val JSON_READER_PARAMETER_DEF: String = "$READER_NAME: $JSON_READER_CLASS_NAME"

/**
 * Generate Java type name of the passed type. Type may be parameterized.
 */
internal fun writeJavaTypeName(arg: Type, out: TextOutput) {
  if (arg is Class<*>) {
    val name = arg.canonicalName
    out.append(
        if (name == "java.util.List") "List"
        else if (name == "java.lang.String") "String?"
        else name
    )
  }
  else if (arg is ParameterizedType) {
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
  else if (arg is WildcardType) {
    val upperBounds = arg.upperBounds!!
    if (upperBounds.size != 1) {
      throw RuntimeException()
    }
    out.append("? extends ")
    writeJavaTypeName(upperBounds.first(), out)
  }
  else {
    out.append(arg.toString())
  }
}