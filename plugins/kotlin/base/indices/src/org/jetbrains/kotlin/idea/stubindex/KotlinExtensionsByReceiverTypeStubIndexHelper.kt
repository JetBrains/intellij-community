// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration

abstract class KotlinExtensionsByReceiverTypeStubIndexHelper : KotlinStringStubIndexHelper<KtCallableDeclaration>(
    KtCallableDeclaration::class.java
) {
    // Used by third-party plugin `com.gmail.blueboxware.extsee`
    @Suppress("unused")
    @Deprecated("Use org.jetbrains.kotlin.idea.stubindex.KotlinExtensionsByReceiverTypeStubIndexHelper.Companion.Key instead")
    @ApiStatus.ScheduledForRemoval
    fun receiverTypeNameFromKey(key: String): String = key.substringBefore(SEPARATOR, "")


    companion object {

        private const val SEPARATOR = '\n'

        data class Key(
            val receiverTypeName: Name,
            val callableName: Name,
        ) {

            constructor(
                receiverTypeIdentifier: @NonNls String,
                callableIdentifier: @NonNls String,
            ) : this(
                receiverTypeName = Name.identifier(receiverTypeIdentifier),
                callableName = Name.identifier(callableIdentifier),
            )

            constructor(key: @NonNls String) : this(
                receiverTypeIdentifier = key.substringBefore(SEPARATOR, ""),
                callableIdentifier = key.substringAfter(SEPARATOR, ""),
            )

            val key: @NonNls String
                get() = receiverTypeName.identifier + SEPARATOR + callableName.identifier
        }
    }
}