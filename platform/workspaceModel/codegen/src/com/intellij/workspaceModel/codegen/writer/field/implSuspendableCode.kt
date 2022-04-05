package deft.storage.codegen.field

import deft.storage.codegen.javaName
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.implSuspendableCode: String
    get() = "override suspend fun get${javaName.capitalize()}(): ${type.javaType} = $javaName"
