package deft.storage.codegen.field

import deft.storage.codegen.javaFullName
import org.jetbrains.deft.impl.*

val ValueType<*>.kotlinType: String
  get() = javaType

val ValueType<*>.javaBuilderType: String
  get() = javaMutableType

val ValueType<*>.defCode: String
  get() = when (this) {
    TBoolean -> "TBoolean"
    TInt -> "TInt"
    TString -> "TString"
    is TRef<*> -> "TRef(\"${targetObjTypeId.module.notation}\", ${targetObjType.id}${childFlag})"
    is TList<*> -> "TList(${elementType.defCode})"
    is TMap<*, *> -> "TMap(${keyType.defCode}, ${valueType.defCode})"
    is TStructure<*, *> -> "${box.javaFullName}.structure"
    is TOptional<*> -> "TOptional(${type.defCode})"
    is TBlob<*> -> "TBlob(\"$javaSimpleName\")"
  }

val TRef<*>.childFlag: String
  get() = if (child) ", child = true" else ""

val ValueType<*>.javaType: String
  get() = when (this) {
    TBoolean -> "Boolean"
    TInt -> "Int"
    TString -> "String"
    is TList<*> -> "List<${elementType.javaType}>"
    is TMap<*, *> -> "Map<${keyType.javaType}, ${valueType.javaType}>"
    is TRef -> targetObjType.javaFullName
    is TStructure<*, *> -> box.javaFullName
    is TOptional<*> -> type.javaType + "?"
    is TBlob<*> -> javaSimpleName
  }

val ValueType<*>.javaMutableType: String
  get() = when (this) {
    TBoolean -> "Boolean"
    TInt -> "Int"
    TString -> "String"
    is TList<*> -> "MutableList<${elementType.javaType}>"
    is TMap<*, *> -> "MutableMap<${keyType.javaType}, ${valueType.javaType}>"
    is TRef -> targetObjType.javaFullName
    is TStructure<*, *> -> box.javaFullName
    is TOptional<*> -> type.javaMutableType + "?"
    is TBlob<*> -> javaSimpleName
  }

fun ValueType<*>.unsupportedTypeError(): Nothing = error("Unsupported field type: $this")