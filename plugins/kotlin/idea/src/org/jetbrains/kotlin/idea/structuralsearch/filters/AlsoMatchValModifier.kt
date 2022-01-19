// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.structuralsearch.filters

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.KotlinBundle

class AlsoMatchValModifier : OneStateFilter(
    KotlinBundle.lazyMessage("modifier.match.val"),
    KotlinBundle.message("modifier.also.match.val"),
    CONSTRAINT_NAME
) {
    companion object {
        const val CONSTRAINT_NAME: @NonNls String = "kotlinAlsoMatchVal"
    }
}