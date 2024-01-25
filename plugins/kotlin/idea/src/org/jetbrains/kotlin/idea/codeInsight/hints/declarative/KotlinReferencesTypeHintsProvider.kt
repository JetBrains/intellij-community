// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints.declarative

import org.jetbrains.kotlin.idea.codeInsight.hints.HintType
import org.jetbrains.kotlin.idea.codeInsight.hints.NamedInlayInfoOption

val SHOW_PROPERTY_TYPES = NamedInlayInfoOption("hints.type.property")
val SHOW_LOCAL_VARIABLE_TYPES = NamedInlayInfoOption("hints.type.variable")
val SHOW_FUNCTION_RETURN_TYPES = NamedInlayInfoOption("hints.type.function.return")
val SHOW_FUNCTION_PARAMETER_TYPES = NamedInlayInfoOption("hints.type.function.parameter")

class KotlinReferencesTypeHintsProvider : AbstractKotlinInlayHintsProvider(
    HintType.PROPERTY_HINT, HintType.LOCAL_VARIABLE_HINT, HintType.FUNCTION_HINT, HintType.PARAMETER_TYPE_HINT
)