package org.jetbrains.protocolReader;

class TypeRef<T> {
  final Class<T> typeClass;
  TypeHandler<T> type;

  TypeRef(Class<T> typeClass) {
    this.typeClass = typeClass;
  }
}
