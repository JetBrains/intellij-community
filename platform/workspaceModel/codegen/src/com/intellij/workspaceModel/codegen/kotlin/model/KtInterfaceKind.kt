package org.jetbrains.deft.codegen.kotlin.model

import org.jetbrains.deft.impl.ValueType
import storage.codegen.patcher.*

abstract class KtInterfaceKind {
    abstract fun buildField(fieldNumber: Int, field: DefField, scope: KtScope, type: DefType, diagnostics: Diagnostics)
    abstract fun buildValueType(
        ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
        childAnnotation: KtAnnotation?
    ): ValueType<*>?
}

