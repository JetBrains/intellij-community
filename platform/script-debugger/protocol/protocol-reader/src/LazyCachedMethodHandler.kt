package org.jetbrains.protocolReader

import java.lang.reflect.Method

/**
 * Basic implementation of the method that parses value on demand and store it for a future use
 */
class LazyCachedMethodHandler(private val parser: ValueReader, private val fieldBinding: VolatileFieldBinding) : MethodHandler {
  protected fun writeReturnTypeJava(scope: ClassScope, m: Method, out: TextOutput) {
    val objectValueParser = parser.asJsonTypeParser()
    if (objectValueParser == null) {
      writeJavaTypeName(m.getGenericReturnType(), out)
    }
    else {
      out.append(scope.getTypeImplReference(objectValueParser.type.type!!))
    }
  }

  override fun writeMethodImplementationJava(scope: ClassScope, method: Method, out: TextOutput) {
    out.append("@Override").newLine().append("public ")
    writeReturnTypeJava(scope, method, out)
    out.append(' ')
    appendMethodSignatureJava(method, listOf<String>(), out)

    out.openBlock()
    out.append("if (")
    fieldBinding.writeGetExpression(out)
    out.append(" == null)").openBlock()
    run {
      if (parser.isThrowsIOException()) {
        out.append("try").openBlock()
      }
      run {
        fieldBinding.writeGetExpression(out)
        out.append(" = ")
        parser.writeReadCode(scope, true, scope.output)
        out.semi()
      }
      if (parser.isThrowsIOException()) {
        out.closeBlock()
        out.newLine().append("catch (IOException e)").openBlock()
        out.append("throw new com.google.gson.JsonParseException(e);").closeBlock()
      }
      out.newLine().append(PENDING_INPUT_READER_NAME).append(" = null;")
    }
    out.closeBlock()

    out.newLine().append("return ")
    fieldBinding.writeGetExpression(out)
    out.semi()

    out.closeBlock()
  }
}
