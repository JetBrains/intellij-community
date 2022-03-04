package deft.storage.codegen.field

import deft.storage.codegen.codeTemplate
import deft.storage.codegen.javaFullName
import deft.storage.codegen.javaName
import org.jetbrains.deft.codegen.utils.fqn
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

val Field<*, *>.implBlockingCode: String
    get() = type.implBlockCode(javaName, name, this)

fun ValueType<*>.implBlockCode(javaName: String, name: String, field: Field<*, *>): String {
    return when (this) {
        TInt -> "override var $javaName: ${field.type.javaType} = 0"
        TBoolean -> "override var $javaName: ${field.type.javaType} = false"
        TString -> """            
            @JvmField var _$javaName: String? = null
            override val $javaName: ${field.type.javaType}
                get() = _$javaName${if (field.type is TOptional) "" else "!!"}
                                
        """.trimIndent()
        is TRef -> """
            @JvmField var _${javaName}Id: Int = ObjId.nothingN
            @JvmField var _$javaName: ObjImpl? = null${refDefault(javaName, name, field)}
            override val $javaName: ${field.type.javaType}
                get() {
                    _$javaName = _getRef(_$javaName, _${name}Id)${refDefaultGet(javaName, name, field)}
                    return _$javaName as ${field.type.javaType} 
                }
                
        """.codeTemplate().trimStart('\n')
        is TList<*>, is TMap<*, *> -> """
                @JvmField var _$javaName: ${implType}? = null
                fun _$javaName(): $implType {
                    if (_$javaName == null) {
                        _$javaName = ${this.viewConstructor}
                    }
                    return _$javaName!!
                }
                override val $javaName: ${field.type.javaType}
                    get() = ${if (field.type is TOptional) "_$javaName" else "if (_$javaName == null) $empty else _$javaName()"} 
                                                        
        """.trimIndent()
        is TOptional<*> -> when (type) {
            TInt, TBoolean -> "override var $javaName: ${javaType}? = null"
            else -> type.implBlockCode(javaName, name, field)
        }
        else -> error("Unsupported field type: $this")
    }

}

fun refDefault(javaName: String, name: String, field: Field<*, *>): String {
    return when (field.hasDefault) {
        Field.Default.none -> ""
        Field.Default.plain -> """
            private fun ${javaName}InitDefault(): ObjImpl? {
                val value = super<${field.owner.javaFullName}>.${javaName} as ObjImpl?
                if (value != null) {
                    _setRef(value)
                    _${name}Id = value._id.n
                }
                return value
            }                        
        """.codeTemplate()
        Field.Default.suspend -> TODO()
    }
}

fun refDefaultGet(javaName: String, name: String, field: Field<*, *>): String {
    return when (field.hasDefault) {
        Field.Default.none -> ""
        Field.Default.plain -> "\n                        ?: ${javaName}InitDefault()"
        Field.Default.suspend -> TODO()
    }
}

// todo: it is not compositable actually. replace with `type.wrapperView(this)`
val ValueType<*>.viewConstructor: String
    get() = when (this) {
        is TRef<*> -> "${RefView::class.fqn}(this)"
        is TList<*> -> "${ListView::class.fqn}(this, ${elementType.viewConstructor}, mutableListOf())"
        is TMap<*, *> -> "${MapView::class.fqn}(this, ${keyType.viewConstructor}, ${valueType.viewConstructor}, mutableMapOf())"
        else -> "${ValueView::class.fqn}.id()"
    }

val ValueType<*>.empty: String
    get() = when (this) {
        is TList<*> -> "listOf()"
        is TMap<*, *> -> "mapOf()"
        else -> error(this)
    }

val ValueType<*>.implType: String
    get() = when (this) {
        is TRef -> "${Ref::class.fqn}<$javaType>"
        is TList<*> -> "${ListView::class.fqn}<${elementType.javaType}, ${elementType.implType}>"
        is TMap<*, *> -> "${MapView::class.fqn}<${keyType.javaType}, ${keyType.implType}, ${valueType.javaType}, ${valueType.implType}>"
        else -> javaType
    }