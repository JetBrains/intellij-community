package org.jetbrains.deft.codegen.ijws

import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.intellijWs.IjById
import org.jetbrains.deft.intellijWs.IjFile
import storage.codegen.patcher.KtAnnotations
import storage.codegen.patcher.contains
import storage.codegen.patcher.def

fun Field<out Obj, Any?>.entityFieldAssignment(): String =
    when (type) {
        else -> "$ijName = ${type.toIjWsCode("it.$name", def?.annotations)}"
    }

fun Field<out Obj, Any?>.notEntityFieldSpread(): String =
    when (type) {
        else -> type.toIjWsCode(name, def?.annotations)
    }

fun Field<out Obj, Any?>.dataFieldAssignment(): String =
    when (type) {
        else -> "$ijName = ${type.toIjWsCode(name, def?.annotations)}"
    }

fun ValueType<*>.toIjWsCode(
    v: String,
    annotations: KtAnnotations? = null,
    vRaw: String = v
): String {
    return when (this) {
        is TOptional -> type.toIjWsCode("$v?", annotations, vRaw = v)
        is TBoolean, is TInt -> vRaw
        is TString -> when {
            IjFile::class in annotations -> "$v.toIjFile()"
            else -> vRaw
        }
        is TList<*> -> "$v.map { ${elementType.toIjWsCode("it", annotations)} }"
        is TMap<*, *> -> {
            val keyCode = keyType.toIjWsCode("it")
            val valueCode = valueType.toIjWsCode("it")
            if (keyCode == "it" && valueCode == "it") "$v.toMutableMap()"
            else "$v.toMutableMap(" +
                    "{ $keyCode }, " +
                    "{ $valueCode })"
        }
        else ->
            if (IjById::class in annotations) "$v.ijWsId(c)"
            else "$v.toIjWs(c)"
    }
}
