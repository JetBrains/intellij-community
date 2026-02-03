// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.kotlin.util.AbstractKotlinBundle
import java.util.function.Supplier

@NonNls
private const val BUNDLE = "messages.KotlinReferenceIndexBundle"

internal object KotlinReferenceIndexBundle : AbstractKotlinBundle(BUNDLE) {
    fun message(
        @NonNls @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any,
    ): @Nls String = getMessage(key, *params)

    fun lazyMessage(
        @NonNls @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any,
    ): Supplier<@Nls String> = instance.getLazyMessage(key, *params)
}
