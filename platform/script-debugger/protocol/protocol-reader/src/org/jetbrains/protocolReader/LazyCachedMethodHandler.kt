package org.jetbrains.protocolReader

import java.lang.reflect.Method

/**
 * Basic implementation of the method that parses value on demand and store it for a future use
 */
class LazyCachedMethodHandler(private val parser: ValueReader, private val fieldBinding: VolatileFieldBinding) : MethodHandler() {

  protected fun writeReturnTypeJava(scope: ClassScope, m: Method, out: TextOutput) {
    val objectValueParser = parser.asJsonTypeParser()
    if (objectValueParser == null) {
      Util.writeJavaTypeName(m.getGenericReturnType(), out)
    }
    else {
      out.append(scope.getTypeImplReference(objectValueParser!!.getType().type))
    }
  }

  fun writeMethodImplementationJava(classScope: ClassScope, m: Method, out: TextOutput) {
    out.append("@Override").newLine().append("public ")
    writeReturnTypeJava(classScope, m, out)
    out.append(' ')
    appendMethodSignatureJava(m, listOf<String>(), out)

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
        parser.writeReadCode(classScope, true, classScope.getOutput())
        out.semi()
      }
      if (parser.isThrowsIOException()) {
        out.closeBlock()
        out.newLine().append("catch (IOException e)").openBlock()
        out.append("throw new com.google.gson.JsonParseException(e);").closeBlock()
      }
      out.newLine().append(Util.PENDING_INPUT_READER_NAME).append(" = null;")
    }
    out.closeBlock()

    out.newLine().append("return ")
    fieldBinding.writeGetExpression(out)
    out.semi()

    out.closeBlock()
  }
}
