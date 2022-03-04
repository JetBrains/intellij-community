package deft.storage.codegen.field

import deft.storage.codegen.suspendableGetterName
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.builderImplSuspendableCode: String
    get() = """
        override suspend fun $suspendableGetterName(): ${type.javaType} = 
                result.$suspendableGetterName()
    """.trimIndent()