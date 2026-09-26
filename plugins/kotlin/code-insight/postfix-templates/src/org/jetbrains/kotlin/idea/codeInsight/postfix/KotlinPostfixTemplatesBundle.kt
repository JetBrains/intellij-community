// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

internal object KotlinPostfixTemplatesBundle {
    private const val BUNDLE_FQN: @NonNls String = "messages.KotlinPostfixTemplatesBundle"
    private val BUNDLE = DynamicBundle(KotlinPostfixTemplatesBundle::class.java, BUNDLE_FQN)

    @Nls
    fun message(key: @PropertyKey(resourceBundle = BUNDLE_FQN) String, vararg params: Any): String {
        return BUNDLE.getMessage(key, *params)
    }

    fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE_FQN) String, vararg params: Any): Supplier<String> {
        return BUNDLE.getLazyMessage(key, *params)
    }
}
