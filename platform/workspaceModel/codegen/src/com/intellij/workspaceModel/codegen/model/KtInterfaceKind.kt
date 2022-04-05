// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.deft.codegen.model

import org.jetbrains.deft.impl.ValueType

abstract class KtInterfaceKind {
    abstract fun buildField(fieldNumber: Int, field: DefField, scope: KtScope, type: DefType, diagnostics: Diagnostics)
    abstract fun buildValueType(
        ktInterface: KtInterface?, diagnostics: Diagnostics, ktType: KtType,
        childAnnotation: KtAnnotation?
    ): ValueType<*>?
}

