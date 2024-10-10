// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.ScheduledForRemoval
@Deprecated("Use 'org.jetbrains.kotlin.idea.KotlinIconProvider' instead.")
class KotlinIconProviderBase {
    companion object {
        @ApiStatus.ScheduledForRemoval
        @Deprecated(
            "Use org.jetbrains.kotlin.idea.KotlinIconProvider.getMainClass()' instead.",
            ReplaceWith("KotlinIconProvider.getMainClass(file)")
        )
        fun getMainClass(file: KtFile): KtClassOrObject? {
            return KotlinIconProvider.getMainClass(file)
        }
    }
}