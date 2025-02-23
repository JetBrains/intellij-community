// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.types.KotlinType

@Suppress("unused")
@ApiStatus.ScheduledForRemoval
@Deprecated("Use 'org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester' instead")
object KotlinNameSuggester : AbstractKotlinNameSuggester() {
    fun suggestNamesByType(type: KotlinType, validator: (String) -> Boolean, defaultName: String? = null): List<String> {
        return Fe10KotlinNameSuggester.suggestNamesByType(type, validator, defaultName)
    }
}