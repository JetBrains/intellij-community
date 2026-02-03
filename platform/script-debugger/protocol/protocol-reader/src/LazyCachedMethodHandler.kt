package org.jetbrains.protocolReader

import java.lang.reflect.Method

/**
 * Basic implementation of the method that parses value on demand and store it for a future use
 */
internal class LazyCachedMethodHandler(private val parser: ValueReader, private val fieldBinding: VolatileFieldBinding) : MethodHandler {
  protected fun writeReturnTypeJava(scope: ClassScope, m: Method, out: TextOutput) {
    val objectValueParser = parser.asJsonTypeParser()
    if (objectValueParser == null) {
      writeJavaTypeName(m.genericReturnType, out)
    }
    else {
      out.append(scope.getTypeImplReference(objectValueParser.type.type!!))
    }
  }

  override fun writeMethodImplementationJava(scope: ClassScope, method: Method, out: TextOutput) {
    out.append("override fun ")
    appendMethodSignatureJava(method, listOf(), out)
    out.append(": ")
    writeReturnTypeJava(scope, method, out)

    out.openBlock()
    out.append("if (")
    fieldBinding.writeGetExpression(out)
    out.append(" == null)").block {
      if (parser.isThrowsIOException()) {
        out.append("try").openBlock()
      }

      fieldBinding.writeGetExpression(out)
      out.append(" = ")
      parser.writeReadCode(scope, true, scope.output)

      if (parser.isThrowsIOException()) {
        out.closeBlock()
        out.newLine().append("catch (e: IOException)").openBlock()
        out.append("throw com.google.gson.JsonParseException(e)").closeBlock()
      }
      out.newLine().append(PENDING_INPUT_READER_NAME).append(" = null")
    }

    out.newLine().append("return ")
    fieldBinding.writeGetExpression(out)
    out.append("!!")

    out.closeBlock()
  }
}
