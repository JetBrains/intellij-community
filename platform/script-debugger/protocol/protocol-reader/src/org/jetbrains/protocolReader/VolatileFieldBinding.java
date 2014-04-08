package org.jetbrains.protocolReader;

import java.util.concurrent.atomic.AtomicReferenceArray;

class VolatileFieldBinding {
  private static final String FIELD_NAME_PREFIX = "lazy_";

  private final int position;
  private final FieldTypeInfo fieldTypeInfo;

  public VolatileFieldBinding(int position, FieldTypeInfo fieldTypeInfo) {
    this.position = position;
    this.fieldTypeInfo = fieldTypeInfo;
  }

  public Object get(AtomicReferenceArray<Object> atomicReferenceArray) {
    return atomicReferenceArray.get(position);
  }

  void writeGetExpression(TextOutput out) {
    out.append(FIELD_NAME_PREFIX).append(position);
  }

  void writeFieldDeclaration(ClassScope scope, TextOutput out) {
    out.append("private ");
    fieldTypeInfo.appendValueTypeNameJava(scope, out);
    out.space();
    writeGetExpression(out);
    out.semi();
  }
}
