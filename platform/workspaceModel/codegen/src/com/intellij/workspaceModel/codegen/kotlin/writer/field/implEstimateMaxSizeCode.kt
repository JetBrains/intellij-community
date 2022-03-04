package deft.storage.codegen.field

import deft.storage.codegen.javaName
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.implEstimateMaxSizeCode: String
    get() = type.implEstimateMaxSizeCode(javaName)

fun ValueType<*>.implEstimateMaxSizeCode(javaName: String): String =
    when (val type = this) {
        TBoolean -> "1"
        TInt -> "4"
        TString -> "$javaName.outputMaxBytes"
        is TRef<*> -> "ObjId.bytesCount"
        is TList<*> -> "_$javaName.outputMaxBytes { ${type.elementType.implEstimateMaxSizeCode("it")} }"
        is TMap<*, *> -> "_$javaName.outputMaxBytes(" +
                "{ ${type.keyType.implEstimateMaxSizeCode("it")} }, " +
                "{ ${type.valueType.implEstimateMaxSizeCode("it")} })"
        is TOptional<*> ->
            if (type.type is TRef) "ObjId.bytesCount"
            else "(if (_$javaName == null) 1 else 1 + ${type.type.implEstimateMaxSizeCode(javaName)})"
        else -> "_$javaName.outputMaxBytes"
    }


