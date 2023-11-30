// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.kotlin.util.AbstractKotlinBundle
import org.jetbrains.kotlin.idea.base.resources.BUNDLE as _BUNDLE
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle as _KotlinBundle

@ApiStatus.ScheduledForRemoval
@Deprecated("Please use org.jetbrains.kotlin.idea.base.resources.KotlinBundle instead")
object KotlinBundle: AbstractKotlinBundle(_BUNDLE) {
    @Nls
    @JvmStatic
    fun message(@NonNls @PropertyKey(resourceBundle = _BUNDLE) key: String, vararg params: Any): String =
        _KotlinBundle.message(key, params)
}