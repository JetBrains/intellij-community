package deft.storage.codegen.field

import deft.storage.codegen.javaName
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.implUpdateRefIdsCode: String
    get() = type.implUpdateRefIdsCode(javaName)

fun ValueType<*>.implUpdateRefIdsCode(name: String): String =
    when {
        this is TRef || this is TOptional && type is TRef -> "if (_$name != null) _${name}Id = _$name!!._id.n"
        this is AtomicType<*> || (this is TOptional && type is AtomicType<*>) -> ""
        else -> "_$name?.updateRefIds()"
    }