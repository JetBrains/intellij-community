package deft.storage.codegen.field

import deft.storage.codegen.javaFullName
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.implStoreCode: String
    get() = type.implStoreCode(type.backingFieldName(name), true)

fun ValueType<*>.backingFieldName(name: String): String =
    when {
        this is PrimitiveType<*> -> name
        this is TRef || (this is TOptional && type is TRef) -> "_${name}Id"
        else -> "_$name"
    }

fun ValueType<*>.implStoreCode(v: String, topLevel: Boolean): String =
    when (this) {
        TBoolean -> "output.writeBoolean($v)"
        TInt -> "output.writeInt($v)"
        TString -> "output.writeString($v)"
        is TRef -> "output.writeInt($v)"
        is TList<*> -> "output.writeListView($v) { ${elementType.implUnwrappedStoreCode("it")} }"
        is TMap<*, *> -> "output.writeMapView($v, " +
                "{ ${keyType.implUnwrappedStoreCode("it")} }, " +
                "{ ${valueType.implUnwrappedStoreCode("it")} })"
        is TStructure<*, *> -> "output.writeStructure($v, ${box.javaFullName})"
        is TOptional<*> -> {
            if (topLevel && type is TRef) "output.writeId($v)"
            else "output.writeBoolean($v != null)\n" +
                    "if ($v != null) { ${type.implUnwrappedStoreCode("$v!!")} }"
        }
        else -> error("Unsupported field type: $this")
    }

fun ValueType<*>.implUnwrappedStoreCode(v: String): String =
    when (this) {
        is TRef -> "output.writeRef($v)"
        else -> implStoreCode(v, false)
    }
