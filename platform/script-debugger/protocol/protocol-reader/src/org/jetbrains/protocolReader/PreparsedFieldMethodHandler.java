package org.jetbrains.protocolReader;

import java.lang.reflect.Method;

class PreparsedFieldMethodHandler extends MethodHandler {
  private final String fieldName;

  PreparsedFieldMethodHandler(String fieldName) {
    this.fieldName = fieldName;
  }

  @Override
  void writeMethodImplementationJava(ClassScope scope, Method m, TextOutput out) {
    writeMethodDeclarationJava(out, m);
    out.openBlock();
    if (fieldName != null) {
      out.append("return ").append(FieldLoader.FIELD_PREFIX).append(fieldName).semi();
    }
    out.closeBlock();
  }
}
