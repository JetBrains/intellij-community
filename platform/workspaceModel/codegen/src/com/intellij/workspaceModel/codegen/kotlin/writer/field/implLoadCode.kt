package deft.storage.codegen.field

import deft.storage.codegen.javaFullName
import deft.storage.codegen.javaName
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.implLoadCode: String
    get() {
        val type = type
        return when {
            type is PrimitiveType<*> -> "$name = ${type.implLoadCode}"
            type is TRef || (type is TOptional && type.type is TRef) -> "_${name}Id = data.readInt()"
            type is TList<*> -> "data.readListView({ _$javaName() }, { ${type.elementType.implUnwrappedLoadCode} })"
            type is TMap<*, *> -> "data.readMapView({ _$javaName() }, { ${type.keyType.implUnwrappedLoadCode} }, { ${type.valueType.implUnwrappedLoadCode} })"
            else -> "_$name = ${type.implLoadCode}"
        }
    }

val ValueType<*>.implLoadCode: String
    get() = when (this) {
        TBoolean -> "data.readBoolean()"
        TInt -> "data.readInt()"
        TString -> "data.readString()"
        is TRef -> "data.readRef()"
        is TList<*> -> "data.readListView({ $viewConstructor }, { ${elementType.implUnwrappedLoadCode} })"
        is TMap<*, *> -> "data.readMapView({ $viewConstructor }, { ${keyType.implUnwrappedLoadCode} }, { ${valueType.implUnwrappedLoadCode} })"
        is TStructure<*, *> -> "data.readStructure(${box.javaFullName})"
        is TOptional<*> -> if (type is TRef) "data.readIdValue()"
            else "if (data.readBoolean()) ${type.implLoadCode} else null"
        else -> error("Unsupported field type: $this")
    }

val ValueType<*>.implUnwrappedLoadCode: String
    get() = when (this) {
        is TRef -> "data.readRef()"
        else -> implLoadCode
    }