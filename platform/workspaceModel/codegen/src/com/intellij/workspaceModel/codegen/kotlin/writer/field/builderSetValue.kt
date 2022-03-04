package org.jetbrains.deft.codegen.field

import deft.storage.codegen.field.javaMutableType
import deft.storage.codegen.field.javaType
import deft.storage.codegen.javaName
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.builderSetValueCode: String
    get() {
        if (!hasSetter) return "error(\"$name is not assignable\")"

        return when (type) {
            is TList<*>,
            is TMap<*, *> -> "$javaName.set(value as ${type.javaType})"
            else -> "$javaName = value as ${type.javaMutableType}"
        }
    }