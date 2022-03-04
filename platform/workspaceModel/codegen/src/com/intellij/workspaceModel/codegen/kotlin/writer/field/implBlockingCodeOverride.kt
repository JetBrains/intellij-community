package deft.storage.codegen.field

import deft.storage.codegen.javaName
import org.jetbrains.deft.impl.AtomicType
import org.jetbrains.deft.impl.TList
import org.jetbrains.deft.impl.TRef
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.implBlockingCodeOverride: String
    get() = """
            override val $javaName: ${type.javaType}
                get() = super.$javaName as ${type.javaType}
                
        """.trimIndent()
