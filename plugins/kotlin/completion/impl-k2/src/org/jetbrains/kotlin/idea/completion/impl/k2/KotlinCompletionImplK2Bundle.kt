// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.kotlin.util.AbstractKotlinBundle
import java.util.function.Supplier

@NonNls
private const val BUNDLE = "messages.KotlinCompletionImplK2Bundle"

object KotlinCompletionImplK2Bundle : AbstractKotlinBundle(BUNDLE) {
    fun message(
        @NonNls @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any,
    ): @Nls String = getMessage(key, *params)

    fun lazyMessage(
        @NonNls @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any,
    ): Supplier<@Nls String> = instance.getLazyMessage(key, *params)
}
