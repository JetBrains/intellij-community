// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.structuralsearch.filters

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

class AlsoMatchCompanionObjectModifier : OneStateFilter(
    KotlinBundle.messagePointer("ssr.modifier.match.companion.object"),
    KotlinBundle.message("ssr.modifier.match.companion.object"),
    CONSTRAINT_NAME
) {
    companion object {
        const val CONSTRAINT_NAME: @NonNls String = "kotlinAlsoMatchCompanionObject"
    }
}