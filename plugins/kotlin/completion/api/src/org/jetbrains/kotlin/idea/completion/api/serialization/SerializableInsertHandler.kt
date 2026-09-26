// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.api.serialization

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import kotlinx.serialization.Polymorphic
import org.jetbrains.kotlin.idea.completion.api.serialization.lookup.LookupModelConverter

/**
 * @see LookupModelConverter
 */
@Polymorphic
interface SerializableInsertHandler : InsertHandler<LookupElement>

fun InsertHandler<out LookupElement>.ensureSerializable(): SerializableInsertHandler {
    return when (this) {
        is SerializableInsertHandler -> this
        else -> error("InsertHandler $this is not serializable, See the KDoc of `${LookupModelConverter::class.qualifiedName}` for details")
    }
}