// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.api.serialization.lookup.model

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableLookupObject
import kotlinx.serialization.Serializable

@ApiStatus.Internal
@Serializable
sealed class LookupObjectModel {
    @Serializable
    data class SerializableLookupObjectModel(
        val lookupObject: SerializableLookupObject,
    ) : LookupObjectModel()

    @Serializable
    data class PsiLookupObjectModel(
        val psiElement: PsiElementModel,
    ) : LookupObjectModel()

    @Serializable
    data class StringLookupObjectModel(
        val string: String,
    ) : LookupObjectModel()
}