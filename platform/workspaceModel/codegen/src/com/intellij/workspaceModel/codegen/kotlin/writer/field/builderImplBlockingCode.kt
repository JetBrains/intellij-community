package deft.storage.codegen.field

import deft.storage.codegen.javaName
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.*

val Field<*, *>.builderImplBlockingCode: String
    get() = type.builderImplBlockingCode(this)

fun ValueType<*>.builderImplBlockingCode(field: Field<*, *>): String = when (this) {
    TBoolean, TInt -> """
            override var ${field.javaName}: ${field.type.javaMutableType}
                get() = result.${field.javaName}
                set(value) {
                    result.${field.javaName} = value
                }
                
        """.trimIndent()
    TString -> """
            override var ${field.javaName}: ${field.type.javaMutableType}
                get() = result.${field.javaName}
                set(value) {
                    result._${field.javaName} = value
                }
                
        """.trimIndent()
    is TRef -> """
            override var ${field.javaName}: ${field.type.javaMutableType}
                get() = result.${field.javaName}
                set(value) {
                    if (value != null) { 
                        val valueImpl = value._implObj
                        result._${field.javaName} = result._setRef(valueImpl)
                        result._${field.javaName}Id = valueImpl._id.n                    
                    } else {
                        result._${field.javaName} = null
                        result._${field.javaName}Id = ObjId.nothingN
                    }
                }                
                
        """.trimIndent()
    is TList<*>, is TMap<*, *> -> """
            override var ${field.javaName}: ${field.type.javaMutableType}
                get() = result._${field.javaName}()
                set(value) {
                    result._${field.javaName}().set(value)
                }
                
    """.trimIndent()
    is TOptional<*> -> type.builderImplBlockingCode(field)
    is TStructure<*, *> -> "//TODO: ${field.javaName}"
    else -> unsupportedTypeError()
}