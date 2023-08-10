// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import org.jetbrains.kotlin.psi.KtCallableDeclaration

abstract class KotlinExtensionsByReceiverTypeStubIndexHelper : KotlinStringStubIndexHelper<KtCallableDeclaration>(
    KtCallableDeclaration::class.java
) {
    fun buildKey(receiverTypeName: String, callableName: String): String = receiverTypeName + SEPARATOR + callableName

    fun receiverTypeNameFromKey(key: String): String = key.substringBefore(SEPARATOR, "")

    fun callableNameFromKey(key: String): String = key.substringAfter(SEPARATOR, "")

    private companion object {
        private const val SEPARATOR = '\n'
    }
}