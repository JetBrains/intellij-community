// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinClassIdSerializer
import org.jetbrains.kotlin.name.ClassId

@ApiStatus.Internal
@Serializable
sealed class CallableInsertionStrategy {
    @Serializable
    object AsCall : CallableInsertionStrategy()

    @Serializable
    object AsIdentifier : CallableInsertionStrategy()

    @Serializable
    object InfixCallableInsertionStrategy : CallableInsertionStrategy()

    @Serializable
    data class WithCallArgs(val args: List<String>) : CallableInsertionStrategy()

    @Serializable
    data class WithSuperDisambiguation(
        @Serializable(with = KotlinClassIdSerializer::class) val superClassId: ClassId,
        val subStrategy: CallableInsertionStrategy
    ) : CallableInsertionStrategy()
}