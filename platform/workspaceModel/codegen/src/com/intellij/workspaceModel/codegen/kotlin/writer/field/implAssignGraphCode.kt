package deft.storage.codegen.field

import org.jetbrains.deft.impl.AtomicType
import org.jetbrains.deft.impl.PrimitiveType
import org.jetbrains.deft.impl.TOptional
import org.jetbrains.deft.impl.TRef
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.implMoveIntoGraph: String
    get() {
        val type = type
        return when {
            type is TRef<*> || (type is TOptional && type.type is TRef<*>) -> "_$name?.ensureInGraph(value)"
            type is AtomicType<*> || (type is TOptional && type.type is AtomicType<*>) -> "" // non ref atomic type
            else -> "_$name?.ensureInGraph(value)"
        }
    }
