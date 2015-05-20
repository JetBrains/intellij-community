package org.jetbrains.protocolReader

class TypeRef<T>(val typeClass: Class<T>) {
  var type: TypeWriter<T>? = null
}
