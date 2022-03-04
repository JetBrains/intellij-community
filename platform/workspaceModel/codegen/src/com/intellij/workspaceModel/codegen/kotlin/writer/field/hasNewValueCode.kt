package deft.storage.codegen.field

import deft.storage.codegen.implFieldName
import deft.storage.codegen.javaFullName
import deft.storage.codegen.javaName
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.TBoolean
import org.jetbrains.deft.impl.TInt
import org.jetbrains.deft.impl.fields.Field

fun Field<*, *>.getHasNewValueCode(implType: ObjType<*, *>): String {
    return when (hasDefault) {
        Field.Default.none -> when (type) {
            is TBoolean -> javaName
            is TInt -> "$javaName != 0"
            else -> "_$javaName != null"
        }
        else -> when {
            open -> "$implFieldName != super<${implType.javaFullName}>.$javaName"
            else -> "false // has final default value"
        }
    }
}
