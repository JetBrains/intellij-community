package org.jetbrains.deft.codegen.kotlin.model

import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.TRef
import org.jetbrains.deft.impl.ValueType
import storage.codegen.patcher.*

object ObjInterface : KtInterfaceKind() {
    override fun buildField(
        fieldNumber: Int,
        field: DefField,
        scope: KtScope,
        type: DefType,
        diagnostics: Diagnostics
    ) {
        field.id = fieldNumber + 1 // todo: persistent ids
        field.toMemberField(scope, type, diagnostics)
    }

    override fun buildValueType(
        ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
        childAnnotation: KtAnnotation?
    ): ValueType<*>? {
        val type = ktInterface?.objType
        if (type == null) {
            diagnostics.add(
                ktType.classifierRange, "Unsupported type: $ktType. " +
                        "Supported: String, Int, Boolean, List, Map, Serializable, subtypes of Obj"
            )
            return null
        }

        return TRef<Obj>(type.def.module.id.notation, type.id, child = childAnnotation != null).also {
            it.targetObjType = type
        }
    }
}