// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.api.serialization.lookup.model

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler


/**
 * @see org.jetbrains.kotlin.idea.completion.api.serialization.lookup.LookupModelConverter
 */
@Serializable
@ApiStatus.Internal
sealed class LookupElementModel {
    abstract val lookupElementString: String
    abstract val lookupObject: LookupObjectModel
    abstract val psiElement: PsiElementModel?


    @Serializable
    data class LookupElementDecoratorModel(
        val delegate: LookupElementModel,
        val decoratorInsertHandler: SerializableInsertHandler?,
        val delegateInsertHandler: SerializableInsertHandler?,
    ) : LookupElementModel() {
        override val lookupElementString: String
            get() = delegate.lookupElementString
        override val lookupObject: LookupObjectModel
            get() = delegate.lookupObject
        override val psiElement: PsiElementModel?
            get() = delegate.psiElement
    }

    @Serializable
    data class LookupElementBuilderModel(
        override val lookupElementString: String,
        override val lookupObject: LookupObjectModel,
        override val psiElement: PsiElementModel?,
        val insertHandler: SerializableInsertHandler?,
        val userdata: Map<String, UserDataValueModel>,
    ) : LookupElementModel()
}


