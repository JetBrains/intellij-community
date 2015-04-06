package org.jetbrains.protocolReader

import java.lang.reflect.Method
import java.util.Arrays

private val STATIC_METHOD_PARAM_NAME_LIST = listOf(READER_NAME)
private val STATIC_METHOD_PARAM_NAME_LIST2 = Arrays.asList(READER_NAME, "nextName")

class ReadDelegate(private val typeHandler: TypeWriter<*>, private val isList: Boolean, hasNextNameParam: Boolean) {
  private val paramNames = if (hasNextNameParam) STATIC_METHOD_PARAM_NAME_LIST2 else STATIC_METHOD_PARAM_NAME_LIST

  fun write(scope: ClassScope, method: Method, out: TextOutput) {
    writeMethodDeclarationJava(out, method, paramNames)
    out.openBlock()
    out.append("return ")
    if (isList) {
      out.append("readObjectArray(").append(READER_NAME).append(", new ").append(scope.requireFactoryGenerationAndGetName(typeHandler)).append(TYPE_FACTORY_NAME_POSTFIX).append("()").append(")")
    }
    else {
      typeHandler.writeInstantiateCode(scope, out)
      out.append('(').append(READER_NAME)
      out.comma().space()
      out.append(if (paramNames.size() == 1) "null" else "nextName")
      out.append(')')
    }
    out.semi()

    out.closeBlock()
  }
}