package org.jetbrains.deft.impl

@Suppress("UNCHECKED_CAST")
fun <V> ValueType<V>.extGetValue(obj: ExtensibleImpl, value: Any?): V =
  when (this) {
    is TOptional<*> -> if (value == null) null else type.extGetValue(obj, value)
    is TRef<*> -> value
    is TList<*> -> value
    is TMap<*, *> -> value
    is TStructure<*, *> -> TODO()
    is TBlob<*> -> value
    else -> value
  } as V

@Suppress("UNCHECKED_CAST")
fun <V> ValueType<V>.extSetValue(obj: ExtensibleImpl, value: V): Any? {
  return value
}
