package org.jetbrains.deft.codegen.ijws.model

import deft.storage.codegen.suspendable
import org.jetbrains.deft.Obj
import org.jetbrains.deft.codegen.kotlin.model.KtInterfaceKind
import org.jetbrains.deft.impl.TBlob
import org.jetbrains.deft.impl.TRef
import org.jetbrains.deft.impl.ValueType
import org.jetbrains.deft.impl.fields.Field
import storage.codegen.patcher.*

open class WsEntityInterface : KtInterfaceKind() {
    override fun buildField(fieldNumber: Int, field: DefField, scope: KtScope, type: DefType, diagnostics: Diagnostics) {
        field.toMemberField(scope, type, diagnostics)
        if (fieldNumber == 0) {
            val entitySource = Field(type, field.id, "entitySource", TBlob<Any>("EntitySource"))
            entitySource.def = field
            entitySource.open = field.open
            if (field.expr) {
                entitySource.hasDefault =
                    if (field.suspend) Field.Default.suspend
                    else Field.Default.plain
            }
            entitySource.content = field.content
            entitySource.suspendable = field.suspend
        }
    }

    override fun buildValueType(ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
                                childAnnotation: KtAnnotation?): ValueType<*>? {
        val type = ktInterface?.objType
        return if (type != null)
            TRef<Obj>(type.def.module.id.notation, type.id, child = childAnnotation != null).also {
                it.targetObjType = type
            }
        else if (ktType.classifier in listOf("VirtualFileUrl", "EntitySource", "PersistentEntityId"))
            TBlob<Any>(ktType.classifier)
        else {
            diagnostics.add(ktType.classifierRange, "Unsupported type: $ktType. " +
                    "Supported: String, Int, Boolean, List, Map, Serializable, Enum, Data and Sealed classes, subtypes of Obj")
            null
        }
    }
}

object WsEntityWithPersistentId: WsEntityInterface()

interface WsPropertyClass

object WsEnum: WsEntityInterface(), WsPropertyClass {
    override fun buildValueType(ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
                                childAnnotation: KtAnnotation?): ValueType<*> {
        return TBlob<Any>(ktType.classifier)
    }
}

object WsData: WsEntityInterface(), WsPropertyClass {
    override fun buildValueType(ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
                                childAnnotation: KtAnnotation?): ValueType<*> {
        return TBlob<Any>(ktType.classifier)
    }
}
object WsSealed: WsEntityInterface(), WsPropertyClass {
    override fun buildValueType(ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
                                childAnnotation: KtAnnotation?): ValueType<*> {
        return TBlob<Any>(ktType.classifier)
    }
}