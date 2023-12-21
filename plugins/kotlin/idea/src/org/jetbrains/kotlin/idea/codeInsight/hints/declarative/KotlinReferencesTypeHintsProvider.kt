// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints.declarative

import org.jetbrains.kotlin.idea.codeInsight.hints.HintType

class KotlinReferencesTypeHintsProvider : AbstractKotlinInlayHintsProvider(
    HintType.PROPERTY_HINT, HintType.LOCAL_VARIABLE_HINT, HintType.FUNCTION_HINT, HintType.PARAMETER_TYPE_HINT
) {

    companion object {
        const val SHOW_PROPERTY_TYPES = "hints.type.property"
        const val SHOW_LOCAL_VARIABLE_TYPES = "hints.type.variable"
        const val SHOW_FUNCTION_RETURN_TYPES = "hints.type.function.return"
        const val SHOW_FUNCTION_PARAMETER_TYPES = "hints.type.function.parameter"
    }

}