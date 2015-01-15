package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;

class EnumReader<T extends Enum<T>> extends ValueReader {
  public static <T extends Enum<T>> EnumReader<T> create(Class<T> enumTypeClass) {
    return new EnumReader<>(enumTypeClass);
  }

  private final Class<T> enumClass;

  private EnumReader(Class<T> enumClass) {
    super();
    this.enumClass = enumClass;
  }

  @Override
  public void appendFinishedValueTypeName(@NotNull TextOutput out) {
    out.append(enumClass.getCanonicalName());
  }

  @Override
  void writeReadCode(ClassScope scope, boolean subtyping, @NotNull TextOutput out) {
    beginReadCall("Enum", subtyping, out);
    out.comma().append(enumClass.getCanonicalName()).append(".class").append(')');
  }
}
