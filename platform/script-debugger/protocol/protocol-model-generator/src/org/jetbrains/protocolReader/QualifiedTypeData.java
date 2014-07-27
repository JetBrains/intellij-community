package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;

class QualifiedTypeData {
  private final BoxableType type;
  private final boolean optional;
  private final boolean nullable;
  private final boolean asRawString;

  QualifiedTypeData(@NotNull BoxableType type, boolean optional) {
    this(type, optional, false, false);
  }

  QualifiedTypeData(@NotNull BoxableType type, boolean optional, boolean nullable, boolean asRawString) {
    this.type = type;
    this.optional = optional;
    this.nullable = nullable;
    this.asRawString = asRawString;
  }

  boolean isNullable() {
    return nullable;
  }

  @NotNull
  BoxableType getType() {
    return type;
  }

  void writeAnnotations(@NotNull TextOutput out) {
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
