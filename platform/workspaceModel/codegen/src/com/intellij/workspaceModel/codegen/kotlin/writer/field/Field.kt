package deft.storage.codegen

import deft.storage.codegen.field.*
import org.jetbrains.deft.codegen.model.CodegenTypes
import org.jetbrains.deft.getExtension
import org.jetbrains.deft.impl.TBoolean
import org.jetbrains.deft.impl.TInt
import org.jetbrains.deft.impl.fields.*

val MemberOrExtField<*, *>.javaName: String
    get() = name

val MemberOrExtField<*, *>.implFieldName: String
    get() = when (type) {
        is TInt, is TBoolean -> javaName
        else -> "_$javaName"
    }

val Field<*, *>.suspendableGetterName: String
    get() = "get${javaName.capitalize()}"

val Field<*, *>.javaMetaName: String
    get() = if (javaName in reservedObjTypeNames) "${javaName}Field" else javaName

val Field<*, *>.implCode: String
    get() = buildString {
        if (hasSetter) {
            if (isOverride) append(implBlockingCodeOverride)
            else append(implBlockingCode)

            if (suspendable == true) append("\n").append(implSuspendableCode)
        } else {
            when (hasDefault) {
                Field.Default.none -> unreachable()
                Field.Default.plain -> append(
                    """
                        override val $javaName: ${type.javaType}
                            get() = super<${owner.javaFullName}>.$javaName
                                                                                           
                    """.trimIndent()
                )
                Field.Default.suspend -> append(
                    """
                        @Deprecated("Use suspendable getter")
                        override val $javaName: ${type.javaType}
                            get() = runBlocking { $suspendableGetterName() }
                            
                        override suspend fun $suspendableGetterName(): ${type.javaType} = 
                            super<${owner.javaFullName}>.$suspendableGetterName() 
                                                                       
                    """.trimIndent()
                )
            }
        }
    }

val Field<*, *>.builderImplCode: String
    get() = buildString {
        if (hasSetter) {
            append(builderImplBlockingCode)
        } else {
            if (suspendable == true) {
                append(
                    """
                        @Deprecated("Use suspendable getter")                
                        override val $javaName: ${type.javaType}
                            get() = result.$javaName
                                
            """.trimIndent()
                )
            } else {
                append(
                    """                
                        override val $javaName: ${type.javaType}
                            get() = result.$javaName
                                
            """.trimIndent()
                )
            }
        }

        if (suspendable == true) append("\n").append(builderImplSuspendableCode)
    }

val Field<*, *>.isOverride: Boolean
    get() = base != null
            || name == "parent"
            || name == "name"

val MemberOrExtField.Companion.suspendable: ExtField<MemberOrExtField<*, *>, Boolean>
        by CodegenTypes.defExt(1, MemberOrExtField, TBoolean)

var MemberOrExtField<*, *>.suspendable: Boolean?
    get() = getExtension(MemberOrExtField.suspendable)
    set(value) {
        unsafeAddExtension(MemberOrExtField.suspendable, value!!)
    }

val reservedObjTypeNames = mutableSetOf(
    "factory",
    "name",
    "parent",
    "inheritanceAllowed",
    "module",
    "fullId",
    "structure",
    "ival",
    "ivar",
    "module"
)
