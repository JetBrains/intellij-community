package org.jetbrains.deft.codegen.ijws.fields

import deft.storage.codegen.field.javaType
import org.jetbrains.deft.codegen.ijws.getRefType
import org.jetbrains.deft.codegen.ijws.refsFields
import org.jetbrains.deft.codegen.ijws.wsFqn
import org.jetbrains.deft.impl.TList
import org.jetbrains.deft.impl.TOptional
import org.jetbrains.deft.impl.TRef
import org.jetbrains.deft.impl.ValueType
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.impl.fields.MemberOrExtField

val MemberOrExtField<*, *>.refsConnectionId: String
    get() = if (name == "parent") {
        val originalField = owner.structure.refsFields.first { it.type.javaType == type.javaType }
        "${originalField.name.uppercase()}_CONNECTION_ID"
    } else "${name.uppercase()}_CONNECTION_ID"

val MemberOrExtField<*, *>.refsConnectionIdCode: String
    get() = buildString {
        val ref = type.getRefType()
        val isListType = type is TList<*> || ((type as? TOptional<*>)?.type is TList<*>)

        append("/* internal */val $refsConnectionId: ${wsFqn("ConnectionId")} = ConnectionId.create(")
        if (ref.child) {
            append("${owner.name}::class.java, ${ref.javaType}::class.java,")
        } else {
            append("${ref.javaType}::class.java, ${owner.name}::class.java,")
        }
        val isParentNullable = if (ref.child) {
            if (isListType) {
                if (ref.targetObjType.abstract) {
                    append(" ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,")
                } else {
                    append(" ConnectionId.ConnectionType.ONE_TO_MANY,")
                }
            } else {
                if (ref.targetObjType.abstract) {
                    append(" ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,")
                } else {
                    append(" ConnectionId.ConnectionType.ONE_TO_ONE,")
                }
            }
            referencedField.type is TOptional<*>
        } else {
            val declaredReferenceFromParent = referencedField
            var valueType = declaredReferenceFromParent.type
            if (valueType is TOptional<*>) {
                valueType = valueType.type as ValueType<Any?>
            }
            if (valueType is TList<*>) {
                if (owner.abstract) {
                    append(" ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY,")
                } else {
                    append(" ConnectionId.ConnectionType.ONE_TO_MANY,")
                }
            } else if (valueType is TRef<*>) {
                if (owner.abstract) {
                    append(" ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE,")
                } else {
                    append(" ConnectionId.ConnectionType.ONE_TO_ONE,")
                }
            }
            type is TOptional<*>
        }
        append(" $isParentNullable)")
    }

fun Field<*, *>.refsConnectionMethodCode(genericType: String = ""): String {
    val ref = type.getRefType()
    val connectionName = name.uppercase() + "_CONNECTION_ID"
    val getterName = if (ref.child) {
        if (ref.targetObjType.abstract)
            "${wsFqn("extractOneToAbstractOneChild")}$genericType"
        else
            "${wsFqn("extractOneToOneChild")}$genericType"
    } else {
        var valueType = referencedField.type
        if (valueType is TOptional<*>) {
            valueType = valueType.type as ValueType<Any?>
        }
        when (valueType) {
            is TList<*> -> if (owner.abstract)
                "${wsFqn("extractOneToAbstractManyParent")}$genericType"
            else
                "${wsFqn("extractOneToManyParent")}$genericType"
            is TRef<*> -> if (owner.abstract)
                "${wsFqn("extractOneToAbstractOneParent")}$genericType"
            else
                "${wsFqn("extractOneToOneParent")}$genericType"
            else -> error("Unsupported reference type")
        }
    }
    return "$getterName($connectionName, this)"
}