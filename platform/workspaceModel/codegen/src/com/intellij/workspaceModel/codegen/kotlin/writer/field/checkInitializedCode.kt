package deft.storage.codegen.field

import deft.storage.codegen.javaFullName
import deft.storage.codegen.javaMetaName
import deft.storage.codegen.javaName
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.metaRef: String
    get() = "${owner.javaFullName}.${javaMetaName}"

val Field<*, *>.checkInitializedCode: String
    get() {
        if (hasDefault != Field.Default.none) return "" // todo: evaluate default
        return when (type) {
            is TString -> "freezeCheck($metaRef, _$javaName != null)"
            is TRef<*> -> "freezeCheck($metaRef, (_${javaName}Id != ObjId.nothingN && _${javaName}Id != ObjId.newIdHolderN) || _$javaName != null)"
            else -> ""
        }
    }