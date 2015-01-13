package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;

class FieldLoader {
  public static final char FIELD_PREFIX = '_';

  final String name;
  final String jsonName;

  final ValueReader valueReader;

  FieldLoader(@NotNull String name, @NotNull String jsonName, @NotNull ValueReader valueReader) {
    this.name = name;
    this.jsonName = jsonName;
    this.valueReader = valueReader;
  }

  public void writeFieldDeclaration(@NotNull TextOutput out) {
    out.append("private").space();
    valueReader.appendFinishedValueTypeName(out);
    out.space().append(FIELD_PREFIX).append(name);
    if (valueReader instanceof PrimitiveValueReader) {
      String defaultValue = ((PrimitiveValueReader)valueReader).defaultValue;
      if (defaultValue != null) {
        out.append(" = ").append(defaultValue);
      }
    }
    out.semi();
  }
}
