package org.jetbrains.protocolReader;

import java.lang.reflect.Method;
import java.util.Collections;

/**
 * Basic implementation of the method that parses value on demand and store it for a future use
 */
class LazyCachedMethodHandler extends MethodHandler {
  private final ValueReader parser;
  private final VolatileFieldBinding fieldBinding;

  LazyCachedMethodHandler(ValueReader parser, VolatileFieldBinding fieldBinding) {
    this.parser = parser;
    this.fieldBinding = fieldBinding;
  }

  protected void writeReturnTypeJava(ClassScope scope, Method m, TextOutput out) {
    ObjectValueReader<?> objectValueParser = parser.asJsonTypeParser();
    if (objectValueParser == null) {
      Util.writeJavaTypeName(m.getGenericReturnType(), out);
    }
    else {
      out.append(scope.getTypeImplReference(objectValueParser.getType().get()));
    }
  }

  @Override
  void writeMethodImplementationJava(ClassScope classScope, Method m, TextOutput out) {
    out.append("@Override").newLine().append("public ");
    writeReturnTypeJava(classScope, m, out);
    out.append(' ');
    appendMethodSignatureJava(m, Collections.<String>emptyList(), out);

    out.openBlock();
    out.append("if (");
    fieldBinding.writeGetExpression(out);
    out.append(" == null)").openBlock();
    {
      if (parser.isThrowsIOException()) {
        out.append("try").openBlock();
      }
      {
        fieldBinding.writeGetExpression(out);
        out.append(" = ");
        parser.writeReadCode(classScope, true, null, classScope.getOutput());
        out.semi();
      }
      if (parser.isThrowsIOException()) {
        out.closeBlock();
        out.newLine().append("catch (IOException e)").openBlock();
        out.append("throw new com.google.gson.JsonParseException(e);").closeBlock();
      }
      out.newLine().append(Util.PENDING_INPUT_READER_NAME).append(" = null;");
    }
    out.closeBlock();

    out.newLine().append("return ");
    fieldBinding.writeGetExpression(out);
    out.semi();

    out.closeBlock();
  }
}
