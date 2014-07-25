package org.jetbrains.protocolReader;

class QualifiedTypeData {
  private final BoxableType typeRef;
  private final boolean optional;
  private final boolean nullable;
  private final boolean asRawString;

  QualifiedTypeData(BoxableType typeRef, boolean optional, boolean nullable, boolean asRawString) {
    this.typeRef = typeRef;
    this.optional = optional;
    this.nullable = nullable;
    this.asRawString = asRawString;
  }

  boolean isNullable() {
    return nullable;
  }

  BoxableType getJavaType() {
    return typeRef;
  }

  void writeAnnotations(TextOutput out) {
    if (optional || asRawString) {
      out.append("@org.chromium.protocolReader.JsonField(");
      if (optional) {
        out.append("optional=true");
      }
      if (asRawString) {
        if (optional) {
          out.append(", ");
        }
        out.append("allowAnyPrimitiveValue=true");
      }
      out.append(")").newLine();
    }

    if (isNullable()) {
      out.append("@org.chromium.protocolReader.JsonNullable").newLine();
    }
  }
}
