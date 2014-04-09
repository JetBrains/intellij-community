package org.jetbrains.protocolReader;

class TypeRef<T> {
  final Class<T> typeClass;
  private TypeHandler<T> type;

  TypeRef(Class<T> typeClass) {
    this.typeClass = typeClass;
  }

  Class<?> getTypeClass() {
    return typeClass;
  }

  TypeHandler<T> get() {
    return type;
  }

  void set(TypeHandler<?> type) {
    //noinspection unchecked
    this.type = (TypeHandler<T>)type;
  }
}
